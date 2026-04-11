package com.agentengine.web.assistant.model;

import lombok.Data;

import java.util.List;

@Data
public class AssistantStateTransitionRequest {
    private String taskId;
    private String userId;
    private LlmAgentState nextState;
    private String lastMessage;
    private String lastToolName;
    private List<String> missingSlots;
    private String errorMessage;
    private Integer lastEmbeddingDim;
}
