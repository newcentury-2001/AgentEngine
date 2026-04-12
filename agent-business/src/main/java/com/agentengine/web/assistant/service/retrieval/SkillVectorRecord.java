package com.agentengine.web.assistant.service.retrieval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillVectorRecord {
    private String skillName;
    private String skillDescription;
    private String intent;
    private double[] vector;
}
