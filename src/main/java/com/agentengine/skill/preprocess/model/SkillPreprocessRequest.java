package com.agentengine.skill.preprocess.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SkillPreprocessRequest(
        @JsonAlias("skillName") String serverLabel,
        String skillDescription,
        String curlExample,
        String mcpServerUrl,
        String confirmedIntentTag,
        String confirmedActionType
) {
}
