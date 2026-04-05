package com.agentengine.skill.preprocess.model;

import java.util.List;
import java.util.Map;

public record SkillPreprocessResult(
        String skillName,
        SkillLabelPrediction skillLabel,
        List<ToolInstallView> tools,
        double[] normalizedSkillVector,
        Map<String, Long> recent7dToolCounts,
        List<ToolVector> toolVectors
) {
}
