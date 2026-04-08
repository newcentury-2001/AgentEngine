package com.agentengine.skill.embedding.repository;

import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Embedding 閺佺増宓佹惔鎾存惙娴? * 鐠愮喕鐭楃亸鍡椾紣閸忓嘲鎷伴幎鈧懗鐣屾畱 embedding 閸忋儱绨? */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EmbeddingDbRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 閹靛綊鍣烘穱婵嗙摠瀹搞儱鍙?embedding
     */
    public int[] batchSaveToolEmbeddings(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new int[0];
        }

        String sql = """
            INSERT INTO mcp_tool_vector (skill_name, tool_name, server_url, normalized_vector, updated_at)
            VALUES (?, ?, ?, ?, NOW())
            ON CONFLICT (skill_name, tool_name)
            DO UPDATE SET
                server_url = EXCLUDED.server_url,
                normalized_vector = EXCLUDED.normalized_vector,
                updated_at = NOW()
            """;

        List<Object[]> params = tools.stream()
                .filter(tool -> tool.getEmbedding() != null && tool.getEmbedding().length > 0)
                .map(tool -> new Object[]{
                        tool.getSkillName(),
                        tool.getToolName(),
                        tool.getServerUrl() != null ? tool.getServerUrl() : "",
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
     * 閹靛綊鍣烘穱婵嗙摠閹垛偓閼?embedding
     */
    public int[] batchSaveSkillEmbeddings(List<McpSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return new int[0];
        }

        String sql = """
            INSERT INTO skill_vector_snapshot (skill_name, skill_description, server_url, skill_vector, updated_at)
            VALUES (?, ?, ?, ?, NOW())
            ON CONFLICT (skill_name)
            DO UPDATE SET
                skill_description = EXCLUDED.skill_description,
                server_url = EXCLUDED.server_url,
                skill_vector = EXCLUDED.skill_vector,
                updated_at = NOW()
            """;

        List<Object[]> params = skills.stream()
                .filter(skill -> skill.getEmbedding() != null && skill.getEmbedding().length > 0)
                .map(skill -> new Object[]{
                        skill.getSkillName(),
                        skill.getSkillDescription() != null ? skill.getSkillDescription() : "",
                        skill.getServerUrl() != null ? skill.getServerUrl() : "",
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
     * 缂佺喕顓搁幋鎰閻ㄥ嫭鏆熼柌?     */
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
     * 鐏?double[] 閺佹壆绮嶆潪顒佸床娑?JSON 鐎涙顑佹稉?     */
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

