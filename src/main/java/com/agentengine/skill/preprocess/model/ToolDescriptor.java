package com.agentengine.skill.preprocess.model;

public record ToolDescriptor(
        String name,
        String description,
        String inputSchema,
        String toolUrl
) {
}
