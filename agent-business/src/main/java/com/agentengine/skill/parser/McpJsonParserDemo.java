package com.agentengine.skill.parser;

import com.agentengine.skill.model.InputSlot;
import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.model.OutputSlotInferred;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON解析器演示程序
 */
public class McpJsonParserDemo {

    public static void main(String[] args) {
        try {
            // 解析JSON文件
            System.out.println("=== 开始解析 MCP JSON 文件 ===");
            List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");

            System.out.println("成功解析 " + skills.size() + " 个技能\n");

            // 显示第一个技能的详细信息
            if (!skills.isEmpty()) {
                McpSkill firstSkill = skills.get(0);
                printSkillDetails(firstSkill, 0);
            }

            // 显示统计信息
            Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
            System.out.println("\n=== 统计信息 ===");
            System.out.println("技能数量: " + stats.get("skillCount"));
            System.out.println("工具数量: " + stats.get("toolCount"));
            System.out.println("输入槽位数量: " + stats.get("inputSlotCount"));
            System.out.println("输出槽位数量: " + stats.get("outputSlotCount"));

            // 测试工具扁平化
            System.out.println("\n=== 工具扁平化测试 ===");
            List<McpTool> allTools = McpJsonParser.flattenTools(skills);
            System.out.println("扁平化后工具总数: " + allTools.size());

            // 显示前3个工具信息
            System.out.println("\n前3个工具:");
            for (int i = 0; i < Math.min(3, allTools.size()); i++) {
                McpTool tool = allTools.get(i);
                System.out.println((i + 1) + ". " + tool.getSkillName() + ":" + tool.getToolName());
                System.out.println("   描述: " + tool.getToolDescription());
            }

            // 测试映射功能
            System.out.println("\n=== 映射功能测试 ===");
            Map<String, McpSkill> skillMap = McpJsonParser.buildSkillNameMap(skills);
            System.out.println("技能映射表大小: " + skillMap.size());

            Map<String, McpTool> toolMap = McpJsonParser.buildToolKeyMap(skills);
            System.out.println("工具映射表大小: " + toolMap.size());

            // 测试查找功能
            if (!skills.isEmpty()) {
                McpSkill skill = skills.get(0);
                McpSkill foundSkill = skillMap.get(skill.getSkillName());
                System.out.println("查找技能 '" + skill.getSkillName() + "': " +
                    (foundSkill != null ? "成功" : "失败"));

                if (skill.getTools() != null && !skill.getTools().isEmpty()) {
                    McpTool tool = skill.getTools().get(0);
                    String toolKey = skill.getSkillName() + ":" + tool.getToolName();
                    McpTool foundTool = toolMap.get(toolKey);
                    System.out.println("查找工具 '" + toolKey + "': " +
                        (foundTool != null ? "成功" : "失败"));
                }
            }

            // 测试槽位获取
            System.out.println("\n=== 槽位获取测试 ===");
            List<InputSlot> allInputSlots = McpJsonParser.getAllInputSlots(skills);
            System.out.println("所有输入槽位数量: " + allInputSlots.size());

            List<OutputSlotInferred> allOutputSlots = McpJsonParser.getAllOutputSlots(skills);
            System.out.println("所有输出槽位数量: " + allOutputSlots.size());

            // 显示前3个输入槽位
            System.out.println("\n前3个输入槽位:");
            for (int i = 0; i < Math.min(3, allInputSlots.size()); i++) {
                InputSlot slot = allInputSlots.get(i);
                System.out.println((i + 1) + ". " + slot.getSlotKey() + " -> " + slot.getFieldPath() +
                    " (" + slot.getFieldType() + ")");
            }

            System.out.println("\n=== 测试完成 ===");

        } catch (IOException e) {
            System.err.println("解析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印技能详细信息
     */
    private static void printSkillDetails(McpSkill skill, int index) {
        System.out.println("\n=== 技能 #" + (index + 1) + " ===");
        System.out.println("名称: " + skill.getSkillName());
        System.out.println("描述: " + skill.getSkillDescription());
        System.out.println("意图: " + skill.getIntent());
        System.out.println("动作类型: " + skill.getActionType());

        if (skill.getTools() != null && !skill.getTools().isEmpty()) {
            System.out.println("\n包含 " + skill.getTools().size() + " 个工具:");

            // 只显示前3个工具的详细信息
            for (int i = 0; i < Math.min(3, skill.getTools().size()); i++) {
                McpTool tool = skill.getTools().get(i);
                System.out.println("\n  工具 #" + (i + 1) + ":");
                System.out.println("    名称: " + tool.getToolName());
                System.out.println("    描述: " + tool.getToolDescription());

                if (tool.getInputSlots() != null && !tool.getInputSlots().isEmpty()) {
                    System.out.println("    输入槽位:");
                    for (InputSlot slot : tool.getInputSlots()) {
                        System.out.println("      - " + slot.getSlotKey() + " -> " + slot.getFieldPath() +
                            " (" + slot.getFieldType() + ", " + (slot.isRequired() ? "必填" : "可选") + ")");
                    }
                }

                if (tool.getOutputSlotsInferred() != null && !tool.getOutputSlotsInferred().isEmpty()) {
                    System.out.println("    输出槽位:");
                    for (OutputSlotInferred slot : tool.getOutputSlotsInferred()) {
                        System.out.println("      - " + slot.getSlotKey() + " (置信度: " + slot.getConfidence() + ")");
                    }
                }
            }
        }
    }
}
