package com.agentengine.web.assistant.model;

import lombok.Data;

import java.util.List;

@Data
public class AssistantAgentProcessRequest {
    private String userId;
    private String taskId;
    private String traceId;
    private String message;
    private String selectedIntent;

    private Boolean needTool;
    private Boolean answerReady;
    private String toolName;
    private List<String> missingSlots;
    private String errorMessage;
}
