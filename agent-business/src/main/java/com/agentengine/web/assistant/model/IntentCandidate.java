package com.agentengine.web.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentCandidate {
    private String intentCode;
    private String intentLabel;
    private double score;
}
