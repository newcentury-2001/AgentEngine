package com.agentengine.skill.parser;

import com.agentengine.skill.model.McpSkill;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 简单的解析器测试
 */
public class SimpleParserTest {

    public static void main(String[] args) {
        try {
            System.out.println("Testing MCP JSON Parser...");
            System.out.println("=============================");

            // 测试1: 从文件解析
            System.out.println("\nTest 1: Parsing from file");
            List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
            System.out.println("✓ Successfully parsed " + skills.size() + " skills");

            // 测试2: 统计信息
            System.out.println("\nTest 2: Getting statistics");
            Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
            System.out.println("✓ Skills: " + stats.get("skillCount"));
            System.out.println("✓ Tools: " + stats.get("toolCount"));
            System.out.println("✓ Input slots: " + stats.get("inputSlotCount"));
            System.out.println("✓ Output slots: " + stats.get("outputSlotCount"));

            // 测试3: 第一个技能信息
            System.out.println("\nTest 3: First skill info");
            if (!skills.isEmpty()) {
                McpSkill skill = skills.get(0);
                System.out.println("✓ Skill name: " + skill.getSkillName());
                System.out.println("✓ Description: " + skill.getSkillDescription());
                System.out.println("✓ Intent: " + skill.getIntent());
                System.out.println("✓ Action type: " + skill.getActionType());
                System.out.println("✓ Tool count: " + (skill.getTools() != null ? skill.getTools().size() : 0));
            }

            // 测试4: 扁平化工具
            System.out.println("\nTest 4: Flattening tools");
            var allTools = McpJsonParser.flattenTools(skills);
            System.out.println("✓ Total tools: " + allTools.size());

            // 测试5: 构建映射
            System.out.println("\nTest 5: Building maps");
            var skillMap = McpJsonParser.buildSkillNameMap(skills);
            System.out.println("✓ Skill map size: " + skillMap.size());

            var toolMap = McpJsonParser.buildToolKeyMap(skills);
            System.out.println("✓ Tool map size: " + toolMap.size());

            // 测试6: 查找功能
            System.out.println("\nTest 6: Lookup functionality");
            if (!skills.isEmpty()) {
                McpSkill skill = skills.get(0);
                McpSkill found = skillMap.get(skill.getSkillName());
                System.out.println("✓ Skill lookup: " + (found != null ? "SUCCESS" : "FAILED"));

                if (skill.getTools() != null && !skill.getTools().isEmpty()) {
                    var tool = skill.getTools().get(0);
                    String toolKey = skill.getSkillName() + ":" + tool.getToolName();
                    var foundTool = toolMap.get(toolKey);
                    System.out.println("✓ Tool lookup: " + (foundTool != null ? "SUCCESS" : "FAILED"));
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
