package com.agentengine.skill.preprocess.model;

public record CleanedToolSemantic(
        String toolName,
        String embeddingText,
        String normalizedJson
) {
}
