package com.agentengine.skill.preprocess.model;

public record ToolVector(
        String toolName,
        double[] normalizedVector,
        long recent7dCount,
        double weight
) {
}
