package com.agentengine.skill.preprocess.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SkillPreprocessRequest(
        @JsonAlias("serverLabel") String skillName,
        String skillDescription,
        String curlExample,
        String mcpServerUrl,
        String confirmedIntentTag,
        String confirmedActionType
) {
}
