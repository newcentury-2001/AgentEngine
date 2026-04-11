package com.agentengine.web.assistant.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
@Builder
public class AssistantInferenceResult {
    private boolean needTool;
    private boolean answerReady;
    private String toolName;
    private List<String> missingSlots;
    private String errorMessage;
    private Integer embeddingDim;
    private Map<String, String> entityMemory;
}
