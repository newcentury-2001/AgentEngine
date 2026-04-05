package com.agentengine.skill.preprocess.service;

import com.agentengine.skill.preprocess.config.SkillPreprocessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolCallStatsService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallStatsService.class);
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final SkillPreprocessProperties properties;
    private final DefaultRedisScript<List> snapshotBatchScript;

    private volatile boolean warnedNonPartitionedTable = false;

    public ToolCallStatsService(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate, SkillPreprocessProperties properties) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.snapshotBatchScript = new DefaultRedisScript<>();
        this.snapshotBatchScript.setLocation(new ClassPathResource("lua/tool_count_snapshot_batch.lua"));
        this.snapshotBatchScript.setResultType(List.class);
    }

    public long incrementToolCount(String toolId) {
        String key = "tool:" + toolId + ":count";
        Long v = redisTemplate.opsForValue().increment(key);
        return v == null ? 0L : v;
    }

    public Map<String, Long> snapshotAndResetToolCounts() {
        Map<String, Long> result = new HashMap<>();
        List<String> batch = new ArrayList<>();
        int batchSize = Math.max(1, properties.getRedisScriptBatchSize());
        int scanHint = Math.max(1, properties.getRedisScanCountHint());

        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        if (factory == null) {
            return result;
        }

        ScanOptions options = ScanOptions.scanOptions()
                .match(properties.getRedisToolCountKeyPattern())
                .count(scanHint)
                .build();

        try (RedisConnection connection = factory.getConnection(); Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                batch.add(key);
                if (batch.size() >= batchSize) {
                    runSnapshotBatchScript(batch, result);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                runSnapshotBatchScript(batch, result);
            }
        } catch (Exception e) {
            throw new IllegalStateException("scan redis tool count keys failed", e);
        }

        return result;
    }

    public void saveDailyStats(LocalDate statDate, Map<String, Long> toolCounts) {
        ensureTableAndPartitions(statDate);
        if (toolCounts.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO tool_call_daily_stats (stat_date, tool_name, call_count)
                VALUES (?, ?, ?)
                ON CONFLICT (stat_date, tool_name)
                DO UPDATE SET call_count = EXCLUDED.call_count
                """;
        List<Object[]> params = new ArrayList<>();
        for (Map.Entry<String, Long> e : toolCounts.entrySet()) {
            params.add(new Object[]{statDate, e.getKey(), e.getValue()});
        }
        jdbcTemplate.batchUpdate(sql, params);
    }

    public HashMap<String, Long> getRecent7dToolCounts() {
        LocalDate end = LocalDate.now();
        ensureTableAndPartitions(end);
        LocalDate start = end.minusDays(6);
        String sql = """
                SELECT tool_name, COALESCE(SUM(call_count), 0) AS total_count
                FROM tool_call_daily_stats
                WHERE stat_date BETWEEN ? AND ?
                GROUP BY tool_name
                """;
        HashMap<String, Long> out = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, start, end);
        for (Map<String, Object> row : rows) {
            String toolName = String.valueOf(row.get("tool_name"));
            long total = ((Number) row.get("total_count")).longValue();
            out.put(toolName, total);
        }
        return out;
    }

    /**
     * 仅回写工具层热度，不更新任何 skill 索引。
     * 口径：近 7 日调用总次数 recent_7d_count，热度 heat_weight=sigmoid(log10(w+1))。
     *
     * @return 受影响的工具向量记录条数
     */
    public int refreshToolHeatOnly() {
        LocalDate end = LocalDate.now();
        ensureTableAndPartitions(end);
        LocalDate start = end.minusDays(6);
        String sql = """
                WITH recent AS (
                    SELECT tool_name, COALESCE(SUM(call_count), 0) AS total_count
                    FROM tool_call_daily_stats
                    WHERE stat_date BETWEEN ? AND ?
                    GROUP BY tool_name
                )
                UPDATE mcp_tool_vector v
                SET
                    recent_7d_count = COALESCE(r.total_count, 0),
                    heat_weight = 1.0 / (1.0 + EXP(-LOG(10, COALESCE(r.total_count, 0) + 1.0))),
                    updated_at = NOW()
                FROM (
                    SELECT v2.skill_name, v2.tool_name, recent.total_count
                    FROM mcp_tool_vector v2
                    LEFT JOIN recent ON recent.tool_name = v2.tool_name
                ) r
                WHERE v.skill_name = r.skill_name AND v.tool_name = r.tool_name
                """;
        return jdbcTemplate.update(sql, start, end);
    }

    private void runSnapshotBatchScript(List<String> batchKeys, Map<String, Long> aggregate) {
        List<?> raw;
        try {
            raw = redisTemplate.execute(snapshotBatchScript, List.of(), batchKeys.toArray());
        } catch (DataAccessException e) {
            throw new IllegalStateException("run snapshot lua script failed", e);
        }
        mergeFlatArray(raw, aggregate);
    }

    private void mergeFlatArray(List<?> raw, Map<String, Long> aggregate) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            String tool = redisObjToString(raw.get(i));
            String countStr = redisObjToString(raw.get(i + 1));
            if (tool == null || tool.isBlank() || countStr == null || countStr.isBlank()) {
                continue;
            }
            try {
                long count = Long.parseLong(countStr);
                aggregate.merge(tool, count, Long::sum);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private String redisObjToString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(obj);
    }

    private void ensureTableAndPartitions(LocalDate anchorDate) {
        createParentPartitionedTableIfNeeded();
        if (!isPartitionedTable()) {
            if (!warnedNonPartitionedTable) {
                warnedNonPartitionedTable = true;
                log.warn("table tool_call_daily_stats exists but is not partitioned; skip weekly partition creation");
            }
            return;
        }

        for (int i = -2; i <= 2; i++) {
            LocalDate d = anchorDate.plusWeeks(i);
            ensureWeeklyPartition(d);
        }
    }

    private void createParentPartitionedTableIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tool_call_daily_stats (
                    stat_date DATE NOT NULL,
                    tool_name VARCHAR(200) NOT NULL,
                    call_count BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (stat_date, tool_name)
                ) PARTITION BY RANGE (stat_date)
                """);
    }

    private boolean isPartitionedTable() {
        String sql = """
                SELECT c.relkind
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = 'tool_call_daily_stats'
                """;
        List<String> kinds = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        return !kinds.isEmpty() && "p".equals(kinds.get(0));
    }

    private void ensureWeeklyPartition(LocalDate dateInWeek) {
        LocalDate weekStart = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);
        String partitionName = "tool_call_daily_stats_p" + weekStart.format(BASIC_DATE);
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF tool_call_daily_stats FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, weekStart, weekEnd
        );
        jdbcTemplate.execute(sql);
    }
}
