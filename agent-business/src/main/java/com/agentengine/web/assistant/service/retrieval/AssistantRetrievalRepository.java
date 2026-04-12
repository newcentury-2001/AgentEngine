package com.agentengine.web.assistant.service.retrieval;

import com.agentcommon.mcp.model.InputSlot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AssistantRetrievalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<SkillVectorRecord> loadSkillVectors() {
        String sql = """
                SELECT skill_name, skill_description, intent, skill_vector
                FROM skill_vector_snapshot
                WHERE skill_vector IS NOT NULL
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SkillVectorRecord(
                text(rs.getString("skill_name")),
                text(rs.getString("skill_description")),
                text(rs.getString("intent")),
                parseVector(text(rs.getString("skill_vector")))
        ));
    }

    public List<ToolVectorRecord> loadToolsBySkill(String skillName) {
        String sql = """
                SELECT v.skill_name, v.tool_name, s.tool_description, s.server_url, s.tool_url, s.input_slots, v.normalized_vector, v.heat_weight
                FROM mcp_tool_vector v
                LEFT JOIN mcp_tool_semantic s ON s.skill_name = v.skill_name AND s.tool_name = v.tool_name
                WHERE v.skill_name = ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ToolVectorRecord(
                text(rs.getString("skill_name")),
                text(rs.getString("tool_name")),
                text(rs.getString("tool_description")),
                text(rs.getString("server_url")),
                text(rs.getString("tool_url")),
                parseInputSlots(text(rs.getString("input_slots"))),
                parseVector(text(rs.getString("normalized_vector"))),
                rs.getDouble("heat_weight")
        ), skillName);
    }

    private double[] parseVector(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return new double[0];
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            if (!node.isArray() || node.isEmpty()) {
                return new double[0];
            }
            double[] result = new double[node.size()];
            for (int i = 0; i < node.size(); i++) {
                result[i] = node.get(i).asDouble(0D);
            }
            return result;
        } catch (Exception e) {
            log.warn("failed to parse vector json", e);
            return new double[0];
        }
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
}
