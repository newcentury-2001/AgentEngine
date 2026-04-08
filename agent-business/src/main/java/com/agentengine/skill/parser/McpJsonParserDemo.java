package com.agentengine.skill.parser;

import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;

import java.util.List;

public class McpJsonParserDemo {

    public static void main(String[] args) throws Exception {
        List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
        System.out.println("skills: " + skills.size());

        List<McpTool> tools = McpJsonParser.flattenTools(skills);
        System.out.println("tools: " + tools.size());
    }
}
