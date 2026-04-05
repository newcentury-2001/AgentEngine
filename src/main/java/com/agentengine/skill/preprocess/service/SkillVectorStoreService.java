package com.agentengine.skill.preprocess.service;

import com.agentengine.skill.preprocess.config.SkillPreprocessProperties;
import com.agentengine.skill.preprocess.model.ToolDescriptor;
import com.agentengine.skill.preprocess.model.ToolVector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkillVectorStoreService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final SkillPreprocessProperties properties;

    public SkillVectorStoreService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RedissonClient redissonClient,
            SkillPreprocessProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    /**
     * 判断技能是否允许安装（幂等校验）。
     * <p>
     * 先基于 Redis 分布式锁串行化同名技能安装，再查询数据库是否已存在该技能。
     * 若已存在或未抢到锁，返回 {@code false}；仅当“抢锁成功且库中不存在”时返回 {@code true}。
     */
    public boolean canInstallSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        String lockPrefix = properties.getInstallLockKeyPrefix();
        String lockKey = lockPrefix + skillName;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = lock.tryLock();
        if (!locked) {
            return false;
        }
        try {
            return !existsSkill(skillName);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void save(
            String skillName,
            String skillDescription,
            List<ToolDescriptor> toolDescriptors,
            List<ToolVector> toolVectors,
            double[] normalizedSkillVector
    ) {
        saveToolSemantics(skillName, toolDescriptors);
        saveToolVectors(skillName, toolVectors);
        saveSkillVector(skillName, skillDescription, normalizedSkillVector);
    }

    public void initSchemaIfNeeded() {
        createTablesIfNeeded();
    }

    private void saveToolSemantics(String skillName, List<ToolDescriptor> tools) {
        String sql = """
                INSERT INTO mcp_tool_semantic (skill_name, tool_name, tool_description, input_schema, tool_url)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (skill_name, tool_name)
                DO UPDATE SET
                    tool_description = EXCLUDED.tool_description,
                    input_schema = EXCLUDED.input_schema,
                    tool_url = EXCLUDED.tool_url,
                    updated_at = NOW()
                """;
        for (ToolDescriptor t : tools) {
            jdbcTemplate.update(sql, skillName, t.name(), t.description(), t.inputSchema(), t.toolUrl());
        }
    }

    private void saveToolVectors(String skillName, List<ToolVector> toolVectors) {
        String sql = """
                INSERT INTO mcp_tool_vector (skill_name, tool_name, normalized_vector, recent_7d_count, heat_weight)
                VALUES (?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (skill_name, tool_name)
                DO UPDATE SET
                    normalized_vector = EXCLUDED.normalized_vector,
                    recent_7d_count = EXCLUDED.recent_7d_count,
                    heat_weight = EXCLUDED.heat_weight,
                    updated_at = NOW()
                """;
        for (ToolVector tv : toolVectors) {
            jdbcTemplate.update(sql, skillName, tv.toolName(), toJson(tv.normalizedVector()), tv.recent7dCount(), tv.weight());
        }
    }

    private void saveSkillVector(
            String skillName,
            String skillDescription,
            double[] normalizedSkillVector
    ) {
        String sql = """
                INSERT INTO skill_vector_snapshot (
                    skill_name, skill_description, skill_vector
                )
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (skill_name)
                DO UPDATE SET
                    skill_description = EXCLUDED.skill_description,
                    skill_vector = EXCLUDED.skill_vector,
                    updated_at = NOW()
                """;
        jdbcTemplate.update(
                sql,
                skillName,
                skillDescription,
                toJson(normalizedSkillVector)
        );
    }

    private void createTablesIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_tool_semantic (
                    skill_name VARCHAR(128) NOT NULL,
                    tool_name VARCHAR(200) NOT NULL,
                    tool_description TEXT NOT NULL DEFAULT '',
                    input_schema TEXT NOT NULL DEFAULT '',
                    tool_url TEXT NOT NULL DEFAULT '',
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (skill_name, tool_name)
                )
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'mcp_tool_semantic' AND column_name = 'server_label'
                    ) AND NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'mcp_tool_semantic' AND column_name = 'skill_name'
                    ) THEN
                        ALTER TABLE mcp_tool_semantic RENAME COLUMN server_label TO skill_name;
                    END IF;
                END $$;
                """);
        jdbcTemplate.execute("""
                ALTER TABLE mcp_tool_semantic
                ADD COLUMN IF NOT EXISTS tool_url TEXT NOT NULL DEFAULT ''
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_tool_vector (
                    skill_name VARCHAR(128) NOT NULL,
                    tool_name VARCHAR(200) NOT NULL,
                    normalized_vector JSONB NOT NULL,
                    recent_7d_count BIGINT NOT NULL,
                    heat_weight DOUBLE PRECISION NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (skill_name, tool_name)
                )
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'mcp_tool_vector' AND column_name = 'server_label'
                    ) AND NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'mcp_tool_vector' AND column_name = 'skill_name'
                    ) THEN
                        ALTER TABLE mcp_tool_vector RENAME COLUMN server_label TO skill_name;
                    END IF;
                END $$;
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS skill_vector_snapshot (
                    skill_name VARCHAR(128) PRIMARY KEY,
                    skill_description TEXT NOT NULL DEFAULT '',
                    skill_vector JSONB NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'skill_vector_snapshot' AND column_name = 'server_label'
                    ) AND NOT EXISTS (
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'skill_vector_snapshot' AND column_name = 'skill_name'
                    ) THEN
                        ALTER TABLE skill_vector_snapshot RENAME COLUMN server_label TO skill_name;
                    END IF;
                END $$;
                """);
        jdbcTemplate.execute("""
                ALTER TABLE skill_vector_snapshot
                DROP COLUMN IF EXISTS tool_package_vector
                """);
        jdbcTemplate.execute("""
                ALTER TABLE skill_vector_snapshot
                DROP COLUMN IF EXISTS final_skill_vector
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_vector_snapshot_skill_name
                ON skill_vector_snapshot(skill_name)
                """);
    }

    /**
     * 检查技能是否已存在于技能快照表。
     * 仅用于幂等判断，不做写入操作。
     */
    private boolean existsSkill(String skillName) {
        String sql = "SELECT EXISTS (SELECT 1 FROM skill_vector_snapshot WHERE skill_name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, skillName);
        return Boolean.TRUE.equals(exists);
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }
}
