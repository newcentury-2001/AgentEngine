package com.agentcommon.mcp.parser;

import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.model.OutputSlotInferred;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class McpJsonParser {

    private static final Logger log = LoggerFactory.getLogger(McpJsonParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private McpJsonParser() {
    }

    public static List<McpSkill> parseFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }
        List<McpSkill> skills = OBJECT_MAPPER.readValue(file,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, McpSkill.class));
        setSkillNames(skills);
        log.info("Parsed {} skills from file: {}", skills.size(), filePath);
        return skills;
    }

    public static List<McpSkill> parseFromStream(InputStream inputStream) throws IOException {
        List<McpSkill> skills = OBJECT_MAPPER.readValue(inputStream,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, McpSkill.class));
        setSkillNames(skills);
        log.info("Parsed {} skills from input stream", skills.size());
        return skills;
    }

    public static List<McpSkill> parseFromString(String jsonString) throws IOException {
        List<McpSkill> skills = OBJECT_MAPPER.readValue(jsonString,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, McpSkill.class));
        setSkillNames(skills);
        log.info("Parsed {} skills from JSON string", skills.size());
        return skills;
    }

    private static void setSkillNames(List<McpSkill> skills) {
        for (McpSkill skill : skills) {
            if (skill.getTools() == null) {
                continue;
            }
            for (McpTool tool : skill.getTools()) {
                tool.setSkillName(skill.getSkillName());
                if (tool.getServerUrl() == null || tool.getServerUrl().isBlank()) {
                    tool.setServerUrl(skill.getServerUrl());
                }
            }
        }
    }

    public static List<McpTool> flattenTools(List<McpSkill> skills) {
        List<McpTool> allTools = new ArrayList<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                allTools.addAll(skill.getTools());
            }
        }
        log.info("Flattened {} tools from {} skills", allTools.size(), skills.size());
        return allTools;
    }

    public static Map<String, McpSkill> buildSkillNameMap(List<McpSkill> skills) {
        Map<String, McpSkill> skillMap = new HashMap<>();
        for (McpSkill skill : skills) {
            skillMap.put(skill.getSkillName(), skill);
        }
        return skillMap;
    }

    public static Map<String, McpTool> buildToolKeyMap(List<McpSkill> skills) {
        Map<String, McpTool> toolMap = new HashMap<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() == null) {
                continue;
            }
            for (McpTool tool : skill.getTools()) {
                String key = skill.getSkillName() + ":" + tool.getToolName();
                toolMap.put(key, tool);
            }
        }
        return toolMap;
    }

    public static List<InputSlot> getAllInputSlots(List<McpSkill> skills) {
        List<InputSlot> allSlots = new ArrayList<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() == null) {
                continue;
            }
            for (McpTool tool : skill.getTools()) {
                if (tool.getInputSlots() != null) {
                    allSlots.addAll(tool.getInputSlots());
                }
            }
        }
        return allSlots;
    }

    public static List<OutputSlotInferred> getAllOutputSlots(List<McpSkill> skills) {
        List<OutputSlotInferred> allSlots = new ArrayList<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() == null) {
                continue;
            }
            for (McpTool tool : skill.getTools()) {
                if (tool.getOutputSlotsInferred() != null) {
                    allSlots.addAll(tool.getOutputSlotsInferred());
                }
            }
        }
        return allSlots;
    }

    public static Map<String, Integer> getStatistics(List<McpSkill> skills) {
        Map<String, Integer> stats = new HashMap<>();
        int skillCount = skills.size();
        int toolCount = 0;
        int inputSlotCount = 0;
        int outputSlotCount = 0;

        for (McpSkill skill : skills) {
            if (skill.getTools() == null) {
                continue;
            }
            toolCount += skill.getTools().size();
            for (McpTool tool : skill.getTools()) {
                if (tool.getInputSlots() != null) {
                    inputSlotCount += tool.getInputSlots().size();
                }
                if (tool.getOutputSlotsInferred() != null) {
                    outputSlotCount += tool.getOutputSlotsInferred().size();
                }
            }
        }

        stats.put("skillCount", skillCount);
        stats.put("toolCount", toolCount);
        stats.put("inputSlotCount", inputSlotCount);
        stats.put("outputSlotCount", outputSlotCount);
        return stats;
    }

    public static String toJson(Object obj) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }
}
