package com.agentengine.skill.preprocess.model;

import java.util.List;

public record SkillInstallResponse(
        String serverLabel,
        SkillLabelPrediction skillLabel,
        List<ToolInstallView> tools
) {
    public static SkillInstallResponse from(SkillPreprocessResult result) {
        return new SkillInstallResponse(
                result.serverLabel(),
                result.skillLabel(),
                result.tools()
        );
    }
}
