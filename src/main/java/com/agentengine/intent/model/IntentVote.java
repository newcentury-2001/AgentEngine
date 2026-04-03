package com.agentengine.intent.model;

public record IntentVote(
        String voter,
        IntentTag intentTag,
        double score
) {
}
