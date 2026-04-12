package com.agentengine.web.assistant.model;

import lombok.Data;

import java.util.List;

@Data
public class AssistantStateTransitionRequest {
    private String taskId;
    private String userId;
    private LlmAgentState nextState;
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
