package com.agentengine.web.assistant.service.retrieval;

import com.agentcommon.mcp.model.InputSlot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AssistantRetrievalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<SkillVectorRecord> searchTopSkillsByVector(double[] queryVector, int limit) {
        String sql = """
                SELECT skill_name,
                       skill_description,
                       intent,
                       1 - (skill_vector <=> ?::vector) AS sim_score
                FROM skill_vector_snapshot
                WHERE skill_vector IS NOT NULL
                ORDER BY skill_vector <=> ?::vector
                LIMIT ?
                """;
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SkillVectorRecord(
                text(rs.getString("skill_name")),
                text(rs.getString("skill_description")),
                text(rs.getString("intent")),
                rs.getDouble("sim_score")
        ), literal, literal, Math.max(1, limit));
    }

    public List<SkillVectorRecord> searchTopSkillsByVectorAndIntent(double[] queryVector, String intent, int limit) {
        String sql = """
                SELECT skill_name,
                       skill_description,
                       intent,
                       1 - (skill_vector <=> ?::vector) AS sim_score
                FROM skill_vector_snapshot
                WHERE skill_vector IS NOT NULL
                  AND intent = ?
                ORDER BY skill_vector <=> ?::vector
                LIMIT ?
                """;
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SkillVectorRecord(
                text(rs.getString("skill_name")),
                text(rs.getString("skill_description")),
                text(rs.getString("intent")),
                rs.getDouble("sim_score")
        ), literal, text(intent), literal, Math.max(1, limit));
    }

    public List<ToolVectorRecord> searchTopToolsBySkillAndVector(String skillName, double[] queryVector, int limit) {
        String sql = """
                SELECT v.skill_name,
                       v.tool_name,
                       s.tool_description,
                       s.server_url,
                       s.tool_url,
                       s.input_slots,
                       v.heat_weight,
                       1 - (v.normalized_vector <=> ?::vector) AS sim_score
                FROM mcp_tool_vector v
                LEFT JOIN mcp_tool_semantic s ON s.skill_name = v.skill_name AND s.tool_name = v.tool_name
                WHERE v.skill_name = ?
                  AND v.normalized_vector IS NOT NULL
                ORDER BY v.normalized_vector <=> ?::vector
                LIMIT ?
                """;
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ToolVectorRecord(
                text(rs.getString("skill_name")),
                text(rs.getString("tool_name")),
                text(rs.getString("tool_description")),
                text(rs.getString("server_url")),
                text(rs.getString("tool_url")),
                parseInputSlots(text(rs.getString("input_slots"))),
                rs.getDouble("heat_weight"),
                rs.getDouble("sim_score")
        ), literal, text(skillName), literal, Math.max(1, limit));
    }

    private List<InputSlot> parseInputSlots(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return List.of();
        }
        try {
            List<InputSlot> slots = objectMapper.readValue(jsonArray, new TypeReference<List<InputSlot>>() {});
            return slots == null ? List.of() : slots;
        } catch (Exception e) {
            log.warn("failed to parse input slots json", e);
            return new ArrayList<>();
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String toVectorLiteral(double[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.10f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
