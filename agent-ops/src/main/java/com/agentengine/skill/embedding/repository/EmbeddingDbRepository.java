package com.agentengine.skill.embedding.repository;

import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Embedding 结果落库仓储，负责工具/技能语义与向量的批量写入。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EmbeddingDbRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    /**
     * 批量保存工具语义与工具向量（UPSERT）。
     */
    public int[] batchSaveToolEmbeddings(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new int[0];
        }

        String semanticSql = """
            INSERT INTO mcp_tool_semantic (
                skill_name, tool_name, server_url, tool_description, input_schema, input_slots, tool_url, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (skill_name, tool_name)
            DO UPDATE SET
                server_url = EXCLUDED.server_url,
                tool_description = EXCLUDED.tool_description,
                input_schema = EXCLUDED.input_schema,
                input_slots = EXCLUDED.input_slots,
                tool_url = EXCLUDED.tool_url,
                updated_at = NOW()
            """;

        String vectorSql = """
            INSERT INTO mcp_tool_vector (
                skill_name, tool_name, server_url, normalized_vector, recent_7d_count, heat_weight, updated_at
            )
            VALUES (?, ?, ?, ?::jsonb, 0, 0.5, NOW())
            ON CONFLICT (skill_name, tool_name)
            DO UPDATE SET
                server_url = EXCLUDED.server_url,
                normalized_vector = EXCLUDED.normalized_vector,
                updated_at = NOW()
            """;

        List<Object[]> semanticParams = tools.stream()
                .filter(tool -> tool.getSkillName() != null && tool.getToolName() != null)
                .map(tool -> new Object[]{
                        text(tool.getSkillName()),
                        text(tool.getToolName()),
                        text(tool.getServerUrl()),
                        text(tool.getToolDescription()),
                        toJsonObject(tool.getInputSchema()),
                        toJsonValue(tool.getInputSlots()),
                        text(tool.getServerUrl()),
                })
                .toList();

        List<Object[]> params = tools.stream()
                .filter(tool -> tool.getEmbedding() != null && tool.getEmbedding().length > 0)
                .map(tool -> new Object[]{
                        text(tool.getSkillName()),
                        text(tool.getToolName()),
                        text(tool.getServerUrl()),
                        toJsonArray(tool.getEmbedding())
                })
                .toList();

        if (!semanticParams.isEmpty()) {
            log.info("Saving {} tool semantics to database", semanticParams.size());
            jdbcTemplate.batchUpdate(semanticSql, semanticParams);
        }

        if (params.isEmpty()) {
            log.debug("No valid tools to save");
            return new int[0];
        }

        log.info("Saving {} tool embeddings to database", params.size());
        int[] result = jdbcTemplate.batchUpdate(vectorSql, params);
        int success = countSuccess(result);
        int failed = result.length - success;
        log.info("Batch save completed. Success: {}, Failed: {}",
                success, failed);

        return result;
    }

    /**
     * 批量保存技能语义与技能向量（UPSERT）。
     */
    public int[] batchSaveSkillEmbeddings(List<McpSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return new int[0];
        }

        String semanticSql = """
            INSERT INTO skill_semantic_snapshot (skill_name, skill_description, server_url, intent, tags, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, NOW())
            ON CONFLICT (skill_name)
            DO UPDATE SET
                skill_description = EXCLUDED.skill_description,
                server_url = EXCLUDED.server_url,
                intent = EXCLUDED.intent,
                tags = EXCLUDED.tags,
                updated_at = NOW()
            """;

        String vectorSql = """
            INSERT INTO skill_vector_snapshot (skill_name, skill_description, server_url, intent, tags, skill_vector, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, NOW())
            ON CONFLICT (skill_name)
            DO UPDATE SET
                skill_description = EXCLUDED.skill_description,
                server_url = EXCLUDED.server_url,
                intent = EXCLUDED.intent,
                tags = EXCLUDED.tags,
                skill_vector = EXCLUDED.skill_vector,
                updated_at = NOW()
            """;

        List<Object[]> semanticParams = skills.stream()
                .filter(skill -> skill.getSkillName() != null)
                .map(skill -> new Object[]{
                        text(skill.getSkillName()),
                        text(skill.getSkillDescription()),
                        text(skill.getServerUrl()),
                        text(skill.getIntent()),
                        toJsonValue(skill.getTags())
                })
                .toList();

        List<Object[]> params = skills.stream()
                .filter(skill -> skill.getEmbedding() != null && skill.getEmbedding().length > 0)
                .map(skill -> new Object[]{
                        text(skill.getSkillName()),
                        text(skill.getSkillDescription()),
                        text(skill.getServerUrl()),
                        text(skill.getIntent()),
                        toJsonValue(skill.getTags()),
                        toJsonArray(skill.getEmbedding())
                })
                .toList();

        if (!semanticParams.isEmpty()) {
            log.info("Saving {} skill semantics to database", semanticParams.size());
            jdbcTemplate.batchUpdate(semanticSql, semanticParams);
        }

        if (params.isEmpty()) {
            log.debug("No valid skills to save");
            return new int[0];
        }

        log.info("Saving {} skill embeddings to database", params.size());
        int[] result = jdbcTemplate.batchUpdate(vectorSql, params);
        int success = countSuccess(result);
        int failed = result.length - success;
        log.info("Batch save completed. Success: {}, Failed: {}",
                success, failed);

        return result;
    }

    /**
     * 统计 batchUpdate 返回值中的成功条数。
     */
    private int countSuccess(int[] result) {
        int count = 0;
        for (int num : result) {
            if (num > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 将 double[] 序列化为 JSON 数组字符串。
     */
    private String toJsonArray(double[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(array[i]);
        }
        json.append("]");
        return json.toString();
    }

    private String toJsonObject(Map<String, Object> map) {
        return toJsonValue(map == null ? Collections.emptyMap() : map);
    }

    private String toJsonValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize json value", e);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
