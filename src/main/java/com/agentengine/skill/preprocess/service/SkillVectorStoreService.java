package com.agentengine.skill.preprocess.service;

import com.agentengine.skill.preprocess.model.ToolDescriptor;
import com.agentengine.skill.preprocess.model.ToolVector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkillVectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SkillVectorStoreService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(
            String skillName,
            String skillDescription,
            List<ToolDescriptor> toolDescriptors,
            List<ToolVector> toolVectors,
            double[] normalizedSkillVector,
            double[] normalizedToolPackageVector,
            double[] normalizedFinalSkillVector
    ) {
        createTablesIfNeeded();
        saveToolSemantics(skillName, toolDescriptors);
        saveToolVectors(skillName, toolVectors);
        saveSkillVector(skillName, skillDescription, normalizedSkillVector, normalizedToolPackageVector, normalizedFinalSkillVector);
    }

    private void saveToolSemantics(String skillName, List<ToolDescriptor> tools) {
        String sql = """
                INSERT INTO mcp_tool_semantic (server_label, tool_name, tool_description, input_schema, tool_url)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (server_label, tool_name)
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
                INSERT INTO mcp_tool_vector (server_label, tool_name, normalized_vector, recent_7d_count, heat_weight)
                VALUES (?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (server_label, tool_name)
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
            double[] normalizedSkillVector,
            double[] normalizedToolPackageVector,
            double[] normalizedFinalSkillVector
    ) {
        String sql = """
                INSERT INTO skill_vector_snapshot (
                    server_label, skill_description, skill_vector, tool_package_vector, final_skill_vector
                )
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                ON CONFLICT (server_label)
                DO UPDATE SET
                    skill_description = EXCLUDED.skill_description,
                    skill_vector = EXCLUDED.skill_vector,
                    tool_package_vector = EXCLUDED.tool_package_vector,
                    final_skill_vector = EXCLUDED.final_skill_vector,
                    updated_at = NOW()
                """;
        jdbcTemplate.update(
                sql,
                skillName,
                skillDescription,
                toJson(normalizedSkillVector),
                toJson(normalizedToolPackageVector),
                toJson(normalizedFinalSkillVector)
        );
    }

    private void createTablesIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_tool_semantic (
                    server_label VARCHAR(128) NOT NULL,
                    tool_name VARCHAR(200) NOT NULL,
                    tool_description TEXT NOT NULL DEFAULT '',
                    input_schema TEXT NOT NULL DEFAULT '',
                    tool_url TEXT NOT NULL DEFAULT '',
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (server_label, tool_name)
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE mcp_tool_semantic
                ADD COLUMN IF NOT EXISTS tool_url TEXT NOT NULL DEFAULT ''
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_tool_vector (
                    server_label VARCHAR(128) NOT NULL,
                    tool_name VARCHAR(200) NOT NULL,
                    normalized_vector JSONB NOT NULL,
                    recent_7d_count BIGINT NOT NULL,
                    heat_weight DOUBLE PRECISION NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (server_label, tool_name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS skill_vector_snapshot (
                    server_label VARCHAR(128) PRIMARY KEY,
                    skill_description TEXT NOT NULL DEFAULT '',
                    skill_vector JSONB NOT NULL,
                    tool_package_vector JSONB NOT NULL,
                    final_skill_vector JSONB NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }
}
