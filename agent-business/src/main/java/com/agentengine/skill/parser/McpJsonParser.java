package com.agentengine.skill.parser;

import com.agentengine.skill.model.InputSlot;
import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.model.OutputSlotInferred;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON解析工具类
 * 用于解析dataset/mcp_final_summary.json文件
 */
@Slf4j
public class McpJsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 配置 ObjectMapper
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 从文件路径解析MCP技能列表
     *
     * @param filePath JSON文件路径
     * @return 技能列表
     * @throws IOException 读取文件异常
     */
    public static List<McpSkill> parseFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }
        List<McpSkill> skills = objectMapper.readValue(file,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 设置工具的所属技能名称
        setSkillNames(skills);

        log.info("Parsed {} skills from file: {}", skills.size(), filePath);
        return skills;
    }

    /**
     * 从输入流解析MCP技能列表
     *
     * @param inputStream 输入流
     * @return 技能列表
     * @throws IOException 读取异常
     */
    public static List<McpSkill> parseFromStream(InputStream inputStream) throws IOException {
        List<McpSkill> skills = objectMapper.readValue(inputStream,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 设置工具的所属技能名称
        setSkillNames(skills);

        log.info("Parsed {} skills from input stream", skills.size());
        return skills;
    }

    /**
     * 从JSON字符串解析MCP技能列表
     *
     * @param jsonString JSON字符串
     * @return 技能列表
     * @throws IOException 解析异常
     */
    public static List<McpSkill> parseFromString(String jsonString) throws IOException {
        List<McpSkill> skills = objectMapper.readValue(jsonString,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 设置工具的所属技能名称
        setSkillNames(skills);

        log.info("Parsed {} skills from JSON string", skills.size());
        return skills;
    }

    /**
     * 设置工具的所属技能名称
     *
     * @param skills 技能列表
     */
    private static void setSkillNames(List<McpSkill> skills) {
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                for (McpTool tool : skill.getTools()) {
                    tool.setSkillName(skill.getSkillName());
                }
            }
        }
    }

    /**
     * 将技能列表转换为扁平化的工具列表
     *
     * @param skills 技能列表
     * @return 工具列表
     */
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

    /**
     * 获取技能名称到技能的映射
     *
     * @param skills 技能列表
     * @return 技能名称映射
     */
    public static Map<String, McpSkill> buildSkillNameMap(List<McpSkill> skills) {
        Map<String, McpSkill> skillMap = new HashMap<>();
        for (McpSkill skill : skills) {
            skillMap.put(skill.getSkillName(), skill);
        }
        return skillMap;
    }

    /**
     * 获取工具键值（技能名称:工具名称）到工具的映射
     *
     * @param skills 技能列表
     * @return 工具键值映射
     */
    public static Map<String, McpTool> buildToolKeyMap(List<McpSkill> skills) {
        Map<String, McpTool> toolMap = new HashMap<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                for (McpTool tool : skill.getTools()) {
                    String key = skill.getSkillName() + ":" + tool.getToolName();
                    toolMap.put(key, tool);
                }
            }
        }
        return toolMap;
    }

    /**
     * 获取所有输入槽位
     *
     * @param skills 技能列表
     * @return 槽位列表
     */
    public static List<InputSlot> getAllInputSlots(List<McpSkill> skills) {
        List<InputSlot> allSlots = new ArrayList<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                for (McpTool tool : skill.getTools()) {
                    if (tool.getInputSlots() != null) {
                        allSlots.addAll(tool.getInputSlots());
                    }
                }
            }
        }
        return allSlots;
    }

    /**
     * 获取所有输出槽位推断
     *
     * @param skills 技能列表
     * @return 槽位列表
     */
    public static List<OutputSlotInferred> getAllOutputSlots(List<McpSkill> skills) {
        List<OutputSlotInferred> allSlots = new ArrayList<>();
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                for (McpTool tool : skill.getTools()) {
                    if (tool.getOutputSlotsInferred() != null) {
                        allSlots.addAll(tool.getOutputSlotsInferred());
                    }
                }
            }
        }
        return allSlots;
    }

    /**
     * 统计技能和工具数量
     *
     * @param skills 技能列表
     * @return 统计信息
     */
    public static Map<String, Integer> getStatistics(List<McpSkill> skills) {
        Map<String, Integer> stats = new HashMap<>();
        int skillCount = skills.size();
        int toolCount = 0;
        int inputSlotCount = 0;
        int outputSlotCount = 0;

        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
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
        }

        stats.put("skillCount", skillCount);
        stats.put("toolCount", toolCount);
        stats.put("inputSlotCount", inputSlotCount);
        stats.put("outputSlotCount", outputSlotCount);

        return stats;
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串
     * @throws IOException 转换异常
     */
    public static String toJson(Object obj) throws IOException {
        return objectMapper.writeValueAsString(obj);
    }
}
