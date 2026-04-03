package com.agentengine.skill.preprocess.model;

import com.agentengine.intent.model.ActionType;
import com.agentengine.intent.model.IntentTag;

public record SkillLabelPrediction(
        IntentTag intentTag,
        ActionType actionType,
        double confidence
) {
}
