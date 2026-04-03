package com.agentengine.intent.model;

import java.util.List;

public record IntentDecision(
        IntentTag finalIntent,
        double confidence,
        List<IntentVote> votes
) {
    public boolean shouldFallbackToChat() {
        return finalIntent == null || finalIntent == IntentTag.NONE;
    }
}
