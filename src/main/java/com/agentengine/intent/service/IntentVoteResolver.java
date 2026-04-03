package com.agentengine.intent.service;

import com.agentengine.intent.model.IntentDecision;
import com.agentengine.intent.model.IntentTag;
import com.agentengine.intent.model.IntentVote;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class IntentVoteResolver {

    private static final double MIN_TOP_SCORE = 0.45;
    private static final double MIN_TOP_GAP = 0.10;

    public IntentDecision resolveSingleIntent(List<IntentVote> votes) {
        if (votes == null || votes.isEmpty()) {
            return new IntentDecision(IntentTag.NONE, 0.0, List.of());
        }

        Map<IntentTag, Double> scoreByIntent = new HashMap<>();
        for (IntentVote vote : votes) {
            if (vote == null || vote.intentTag() == null) {
                continue;
            }
            scoreByIntent.merge(vote.intentTag(), vote.score(), Double::sum);
        }
        if (scoreByIntent.isEmpty()) {
            return new IntentDecision(IntentTag.NONE, 0.0, votes);
        }

        List<Map.Entry<IntentTag, Double>> sorted = new ArrayList<>(scoreByIntent.entrySet());
        sorted.sort(Comparator.comparingDouble(Map.Entry<IntentTag, Double>::getValue).reversed());

        IntentTag topIntent = sorted.get(0).getKey();
        double topScore = sorted.get(0).getValue();
        double secondScore = sorted.size() > 1 ? sorted.get(1).getValue() : 0.0;
        double gap = topScore - secondScore;

        if (topScore < MIN_TOP_SCORE || gap < MIN_TOP_GAP) {
            return new IntentDecision(IntentTag.NONE, topScore, votes);
        }
        return new IntentDecision(topIntent, topScore, votes);
    }
}
