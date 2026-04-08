package com.agentengine.skill.parser;

import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.model.OutputSlotInferred;
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
 * MCP JSON瑙ｆ瀽宸ュ叿绫? * 鐢ㄤ簬瑙ｆ瀽dataset/mcp_final_summary.json鏂囦欢
 */
@Slf4j
public class McpJsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 閰嶇疆 ObjectMapper
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 浠庢枃浠惰矾寰勮В鏋怣CP鎶€鑳藉垪琛?     *
     * @param filePath JSON鏂囦欢璺緞
     * @return 鎶€鑳藉垪琛?     * @throws IOException 璇诲彇鏂囦欢寮傚父
     */
    public static List<McpSkill> parseFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }
        List<McpSkill> skills = objectMapper.readValue(file,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 璁剧疆宸ュ叿鐨勬墍灞炴妧鑳藉悕绉?        setSkillNames(skills);

        log.info("Parsed {} skills from file: {}", skills.size(), filePath);
        return skills;
    }

    /**
     * 浠庤緭鍏ユ祦瑙ｆ瀽MCP鎶€鑳藉垪琛?     *
     * @param inputStream 杈撳叆娴?     * @return 鎶€鑳藉垪琛?     * @throws IOException 璇诲彇寮傚父
     */
    public static List<McpSkill> parseFromStream(InputStream inputStream) throws IOException {
        List<McpSkill> skills = objectMapper.readValue(inputStream,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 璁剧疆宸ュ叿鐨勬墍灞炴妧鑳藉悕绉?        setSkillNames(skills);

        log.info("Parsed {} skills from input stream", skills.size());
        return skills;
    }

    /**
     * 浠嶫SON瀛楃涓茶В鏋怣CP鎶€鑳藉垪琛?     *
     * @param jsonString JSON瀛楃涓?     * @return 鎶€鑳藉垪琛?     * @throws IOException 瑙ｆ瀽寮傚父
     */
    public static List<McpSkill> parseFromString(String jsonString) throws IOException {
        List<McpSkill> skills = objectMapper.readValue(jsonString,
            objectMapper.getTypeFactory().constructCollectionType(List.class, McpSkill.class));

        // 璁剧疆宸ュ叿鐨勬墍灞炴妧鑳藉悕绉?        setSkillNames(skills);

        log.info("Parsed {} skills from JSON string", skills.size());
        return skills;
    }

    /**
     * 璁剧疆宸ュ叿鐨勬墍灞炴妧鑳藉悕绉?     *
     * @param skills 鎶€鑳藉垪琛?     */
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
     * 灏嗘妧鑳藉垪琛ㄨ浆鎹负鎵佸钩鍖栫殑宸ュ叿鍒楄〃
     *
     * @param skills 鎶€鑳藉垪琛?     * @return 宸ュ叿鍒楄〃
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
     * 鑾峰彇鎶€鑳藉悕绉板埌鎶€鑳界殑鏄犲皠
     *
     * @param skills 鎶€鑳藉垪琛?     * @return 鎶€鑳藉悕绉版槧灏?     */
    public static Map<String, McpSkill> buildSkillNameMap(List<McpSkill> skills) {
        Map<String, McpSkill> skillMap = new HashMap<>();
        for (McpSkill skill : skills) {
            skillMap.put(skill.getSkillName(), skill);
        }
        return skillMap;
    }

    /**
     * 鑾峰彇宸ュ叿閿€硷紙鎶€鑳藉悕绉?宸ュ叿鍚嶇О锛夊埌宸ュ叿鐨勬槧灏?     *
     * @param skills 鎶€鑳藉垪琛?     * @return 宸ュ叿閿€兼槧灏?     */
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
     * 鑾峰彇鎵€鏈夎緭鍏ユЫ浣?     *
     * @param skills 鎶€鑳藉垪琛?     * @return 妲戒綅鍒楄〃
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
     * 鑾峰彇鎵€鏈夎緭鍑烘Ы浣嶆帹鏂?     *
     * @param skills 鎶€鑳藉垪琛?     * @return 妲戒綅鍒楄〃
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
     * 缁熻鎶€鑳藉拰宸ュ叿鏁伴噺
     *
     * @param skills 鎶€鑳藉垪琛?     * @return 缁熻淇℃伅
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
     * 灏嗗璞¤浆鎹负JSON瀛楃涓?     *
     * @param obj 瀵硅薄
     * @return JSON瀛楃涓?     * @throws IOException 杞崲寮傚父
     */
    public static String toJson(Object obj) throws IOException {
        return objectMapper.writeValueAsString(obj);
    }
}
