package com.agentengine.skill.preprocess.model;

import java.util.List;

public record SkillInstallResponse(
        String skillName,
        SkillLabelPrediction skillLabel,
        List<ToolInstallView> tools
) {
    public static SkillInstallResponse from(SkillPreprocessResult result) {
        return new SkillInstallResponse(
                result.skillName(),
                result.skillLabel(),
                result.tools()
        );
    }
}
