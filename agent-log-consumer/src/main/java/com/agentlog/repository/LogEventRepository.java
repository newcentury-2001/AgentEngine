package com.agentlog.repository;

import com.agentcommon.log.model.LogEvent;
import com.agentlog.model.pojo.LogErrorQuery;
import com.agentlog.model.vo.LogErrorRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class LogEventRepository {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static final String INSERT_SQL = """
            INSERT INTO agent_log_event(
              event_time, app_name, module_name, env_name, host_name,
              level, logger_name, thread_name, trace_id, task_id,
              service_name, method_name, event_type, pool_type, pool_name,
              message, exception_class, stack_trace, mdc_json, ext_json,
              kafka_topic, kafka_partition, kafka_offset
            ) VALUES (
              ?, ?, ?, ?, ?,
              ?, ?, ?, ?, ?,
              ?, ?, ?, ?, ?,
              ?, ?, ?, ?, ?,
              ?, ?, ?
            )
            ON DUPLICATE KEY UPDATE
              ingest_time = ingest_time
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LogEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void batchInsert(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                events,
                events.size(),
                (ps, event) -> {
                    ps.setTimestamp(1, toTimestamp(event.getEventTimeMs()));
                    ps.setString(2, text(event.getAppName()));
                    ps.setString(3, text(event.getModuleName()));
                    ps.setString(4, text(event.getEnvName()));
                    ps.setString(5, text(event.getHostName()));
                    ps.setString(6, text(event.getLevel()));
                    ps.setString(7, text(event.getLoggerName()));
                    ps.setString(8, text(event.getThreadName()));
                    ps.setString(9, text(event.getTraceId()));
                    ps.setString(10, text(event.getTaskId()));
                    ps.setString(11, text(event.getServiceName()));
                    ps.setString(12, text(event.getMethodName()));
                    ps.setString(13, text(event.getEventType()));
                    ps.setString(14, text(event.getPoolType()));
                    ps.setString(15, text(event.getPoolName()));
                    ps.setString(16, text(event.getMessage()));
                    ps.setString(17, text(event.getExceptionClass()));
                    ps.setString(18, text(event.getStackTrace()));
                    ps.setString(19, toJson(event.getMdcJson()));
                    ps.setString(20, toJson(event.getExtJson()));
                    ps.setString(21, text(event.getKafkaTopic()));
                    if (event.getKafkaPartition() == null) {
                        ps.setNull(22, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(22, event.getKafkaPartition());
                    }
                    if (event.getKafkaOffset() == null) {
                        ps.setNull(23, java.sql.Types.BIGINT);
                    } else {
                        ps.setLong(23, event.getKafkaOffset());
                    }
                }
        );
    }

    public long countErrorLogs(LogErrorQuery query) {
        SqlAndArgs sqlAndArgs = buildBaseQuery(query, true);
        Long count = jdbcTemplate.queryForObject(sqlAndArgs.sql(), Long.class, sqlAndArgs.args().toArray());
        return count == null ? 0L : count;
    }

    public List<LogErrorRecord> queryErrorLogs(LogErrorQuery query) {
        SqlAndArgs base = buildBaseQuery(query, false);

        int page = Math.max(1, query.getPage());
        int size = Math.max(1, Math.min(500, query.getSize()));
        int offset = (page - 1) * size;

        String sql = base.sql() + " ORDER BY event_time DESC, id DESC LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(base.args());
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            LogErrorRecord one = new LogErrorRecord();
            one.setId(rs.getLong("id"));
            Timestamp eventTs = rs.getTimestamp("event_time");
            one.setEventTime(eventTs == null ? null : TIME_FMT.format(eventTs.toInstant()));
            one.setLevel(rs.getString("level"));
            one.setEventType(rs.getString("event_type"));
            one.setTraceId(rs.getString("trace_id"));
            one.setTaskId(rs.getString("task_id"));
            one.setServiceName(rs.getString("service_name"));
            one.setMethodName(rs.getString("method_name"));
            one.setMessage(rs.getString("message"));
            one.setExceptionClass(rs.getString("exception_class"));
            one.setPoolType(rs.getString("pool_type"));
            one.setPoolName(rs.getString("pool_name"));
            one.setKafkaTopic(rs.getString("kafka_topic"));
            int partition = rs.getInt("kafka_partition");
            one.setKafkaPartition(rs.wasNull() ? null : partition);
            long offsetValue = rs.getLong("kafka_offset");
            one.setKafkaOffset(rs.wasNull() ? null : offsetValue);
            return one;
        }, args.toArray());
    }

    private SqlAndArgs buildBaseQuery(LogErrorQuery query, boolean countOnly) {
        StringBuilder sql = new StringBuilder();
        if (countOnly) {
            sql.append("SELECT COUNT(1) ");
        } else {
            sql.append("""
                    SELECT id, event_time, level, event_type, trace_id, task_id,
                           service_name, method_name, message, exception_class,
                           pool_type, pool_name, kafka_topic, kafka_partition, kafka_offset
                    """);
        }
        sql.append("FROM agent_log_event WHERE (level = 'ERROR' OR exception_class IS NOT NULL)");

        List<Object> args = new ArrayList<>();
        if (!isBlank(query.getTraceId())) {
            sql.append(" AND trace_id = ?");
            args.add(query.getTraceId().trim());
        }
        if (!isBlank(query.getTaskId())) {
            sql.append(" AND task_id = ?");
            args.add(query.getTaskId().trim());
        }
        if (!isBlank(query.getLevel())) {
            sql.append(" AND level = ?");
            args.add(query.getLevel().trim());
        }
        if (!isBlank(query.getEventType())) {
            sql.append(" AND event_type = ?");
            args.add(query.getEventType().trim());
        }
        if (query.getStartTimeMs() != null && query.getStartTimeMs() > 0) {
            sql.append(" AND event_time >= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(query.getStartTimeMs())));
        }
        if (query.getEndTimeMs() != null && query.getEndTimeMs() > 0) {
            sql.append(" AND event_time <= ?");
            args.add(Timestamp.from(Instant.ofEpochMilli(query.getEndTimeMs())));
        }
        if (!isBlank(query.getKeyword())) {
            sql.append(" AND (message LIKE ? OR exception_class LIKE ? OR event_type LIKE ?)");
            String like = "%" + query.getKeyword().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return new SqlAndArgs(sql.toString(), args);
    }

    private Timestamp toTimestamp(Long eventTimeMs) {
        long value = (eventTimeMs == null || eventTimeMs <= 0L)
                ? System.currentTimeMillis()
                : eventTimeMs;
        return Timestamp.from(Instant.ofEpochMilli(value));
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record SqlAndArgs(String sql, List<Object> args) {
    }
}
