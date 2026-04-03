package com.agentengine.skill.preprocess.model;

import java.util.List;
import java.util.Map;

public record SkillPreprocessResult(
        String serverLabel,
        SkillLabelPrediction skillLabel,
        List<ToolInstallView> tools,
        double[] normalizedSkillVector,
        double[] normalizedToolPackageVector,
        double[] normalizedFinalSkillVector,
        Map<String, Long> recent7dToolCounts,
        List<ToolVector> toolVectors
) {
}
