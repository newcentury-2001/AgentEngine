package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Embedding 数据库操作
 * 负责将工具和技能的 embedding 入库
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EmbeddingDbRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 批量保存工具 embedding
     */
    public int[] batchSaveToolEmbeddings(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new int[0];
        }

        String sql = """
            INSERT INTO mcp_tool_vector (skill_name, tool_name, normalized_vector, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (skill_name, tool_name)
            DO UPDATE SET
                normalized_vector = EXCLUDED.normalized_vector,
                updated_at = NOW()
            """;

        List<Object[]> params = tools.stream()
                .filter(tool -> tool.getEmbedding() != null && tool.getEmbedding().length > 0)
                .map(tool -> new Object[]{
                        tool.getSkillName(),
                        tool.getToolName(),
                        toJsonArray(tool.getEmbedding())
                })
                .toList();

        if (params.isEmpty()) {
            log.debug("No valid tools to save");
            return new int[0];
        }

        log.info("Saving {} tool embeddings to database", params.size());
        int[] result = jdbcTemplate.batchUpdate(sql, params);
        log.info("Batch save completed. Success: {}, Failed: {}",
                countSuccess(result), result.length);

        return result;
    }

    /**
     * 批量保存技能 embedding
     */
    public int[] batchSaveSkillEmbeddings(List<McpSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return new int[0];
        }

        String sql = """
            INSERT INTO skill_vector_snapshot (skill_name, skill_description, skill_vector, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (skill_name)
            DO UPDATE SET
                skill_description = EXCLUDED.skill_description,
                skill_vector = EXCLUDED.skill_vector,
                updated_at = NOW()
            """;

        List<Object[]> params = skills.stream()
                .filter(skill -> skill.getEmbedding() != null && skill.getEmbedding().length > 0)
                .map(skill -> new Object[]{
                        skill.getSkillName(),
                        skill.getSkillDescription() != null ? skill.getSkillDescription() : "",
                        toJsonArray(skill.getEmbedding())
                })
                .toList();

        if (params.isEmpty()) {
            log.debug("No valid skills to save");
            return new int[0];
        }

        log.info("Saving {} skill embeddings to database", params.size());
        int[] result = jdbcTemplate.batchUpdate(sql, params);
        log.info("Batch save completed. Success: {}, Failed: {}",
                countSuccess(result), result.length);

        return result;
    }

    /**
     * 统计成功的数量
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
     * 将 double[] 数组转换为 JSON 字符串
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
}
