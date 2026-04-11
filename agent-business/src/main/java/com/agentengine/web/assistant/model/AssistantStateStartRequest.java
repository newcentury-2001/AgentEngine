package com.agentengine.web.assistant.model;

import lombok.Data;

@Data
public class AssistantStateStartRequest {
    private String userId;
    private String taskId;
    private String traceId;
    private String lastMessage;
}
