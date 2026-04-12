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
    private String intent;
    private String skillName;
    private List<String> missingSlots;
    private String errorMessage;
    private Boolean needClarification;
    private String clarificationType;
    private String clarificationQuestion;
    private List<IntentCandidate> intentCandidatesTop3;
    private String assistantReply;
    private Integer activeTurnCount;
}
