package com.agentengine.skill.embedding.demo;

import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;

import java.util.List;
import java.util.Map;

public class EmbeddingDemo {

    public static void main(String[] args) throws Exception {
        List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
        Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
        List<McpTool> allTools = McpJsonParser.flattenTools(skills);

        System.out.println("skills: " + skills.size());
        System.out.println("tools: " + stats.get("toolCount"));
        System.out.println("sample:");
        for (int i = 0; i < Math.min(5, allTools.size()); i++) {
            McpTool tool = allTools.get(i);
            System.out.println("  - " + tool.getSkillName() + ":" + tool.getToolName());
        }
    }
}
