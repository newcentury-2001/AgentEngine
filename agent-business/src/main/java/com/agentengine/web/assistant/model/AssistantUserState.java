package com.agentengine.web.assistant.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssistantUserState {
    private String userId;
    private String taskId;
    private String traceId;
    private LlmAgentState state;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private String lastMessage;
    private String lastToolName;
    private List<String> missingSlots;
    private String errorMessage;
    private Integer lastEmbeddingDim;
}
