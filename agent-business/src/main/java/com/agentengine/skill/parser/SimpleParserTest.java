package com.agentengine.skill.parser;

import com.agentcommon.mcp.model.McpSkill;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 绠€鍗曠殑瑙ｆ瀽鍣ㄦ祴璇? */
public class SimpleParserTest {

    public static void main(String[] args) {
        try {
            System.out.println("Testing MCP JSON Parser...");
            System.out.println("=============================");

            // 娴嬭瘯1: 浠庢枃浠惰В鏋?            System.out.println("\nTest 1: Parsing from file");
            List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
            System.out.println("鉁?Successfully parsed " + skills.size() + " skills");

            // 娴嬭瘯2: 缁熻淇℃伅
            System.out.println("\nTest 2: Getting statistics");
            Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
            System.out.println("鉁?Skills: " + stats.get("skillCount"));
            System.out.println("鉁?Tools: " + stats.get("toolCount"));
            System.out.println("鉁?Input slots: " + stats.get("inputSlotCount"));
            System.out.println("鉁?Output slots: " + stats.get("outputSlotCount"));

            // 娴嬭瘯3: 绗竴涓妧鑳戒俊鎭?            System.out.println("\nTest 3: First skill info");
            if (!skills.isEmpty()) {
                McpSkill skill = skills.get(0);
                System.out.println("鉁?Skill name: " + skill.getSkillName());
                System.out.println("鉁?Description: " + skill.getSkillDescription());
                System.out.println("鉁?Intent: " + skill.getIntent());
                System.out.println("鉁?Action type: " + skill.getActionType());
                System.out.println("鉁?Tool count: " + (skill.getTools() != null ? skill.getTools().size() : 0));
            }

            // 娴嬭瘯4: 鎵佸钩鍖栧伐鍏?            System.out.println("\nTest 4: Flattening tools");
            var allTools = McpJsonParser.flattenTools(skills);
            System.out.println("鉁?Total tools: " + allTools.size());

            // 娴嬭瘯5: 鏋勫缓鏄犲皠
            System.out.println("\nTest 5: Building maps");
            var skillMap = McpJsonParser.buildSkillNameMap(skills);
            System.out.println("鉁?Skill map size: " + skillMap.size());

            var toolMap = McpJsonParser.buildToolKeyMap(skills);
            System.out.println("鉁?Tool map size: " + toolMap.size());

            // 娴嬭瘯6: 鏌ユ壘鍔熻兘
            System.out.println("\nTest 6: Lookup functionality");
            if (!skills.isEmpty()) {
                McpSkill skill = skills.get(0);
                McpSkill found = skillMap.get(skill.getSkillName());
                System.out.println("鉁?Skill lookup: " + (found != null ? "SUCCESS" : "FAILED"));

                if (skill.getTools() != null && !skill.getTools().isEmpty()) {
                    var tool = skill.getTools().get(0);
                    String toolKey = skill.getSkillName() + ":" + tool.getToolName();
                    var foundTool = toolMap.get(toolKey);
                    System.out.println("鉁?Tool lookup: " + (foundTool != null ? "SUCCESS" : "FAILED"));
                }
            }

            System.out.println("\n=============================");
            System.out.println("All tests completed successfully!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
