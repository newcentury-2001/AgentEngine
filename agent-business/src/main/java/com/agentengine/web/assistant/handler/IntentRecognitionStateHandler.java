package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import org.springframework.stereotype.Component;

@Component
public class IntentRecognitionStateHandler extends AbstractAssistantStateHandler {

    public IntentRecognitionStateHandler(AssistantStateMachineService stateMachineService) {
        super(stateMachineService);
    }

    @Override
    public LlmAgentState state() {
        return LlmAgentState.INTENT_RECOGNITION;
    }

    @Override
    public AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request) {
        if (notBlank(request.getErrorMessage())) {
            return move(current, LlmAgentState.FAILED, request.getMessage(), request.getToolName(),
                    request.getMissingSlots(), request.getErrorMessage(), request.getEmbeddingDim());
        }
        if (request.getMissingSlots() != null && !request.getMissingSlots().isEmpty()) {
            return move(current, LlmAgentState.SLOT_CLARIFICATION, request.getMessage(), request.getToolName(),
                    request.getMissingSlots(), null, request.getEmbeddingDim());
        }
        if (Boolean.TRUE.equals(request.getNeedTool())) {
            return move(current, LlmAgentState.TOOL_EXECUTION, request.getMessage(), request.getToolName(),
                    null, null, request.getEmbeddingDim());
        }
        return move(current, LlmAgentState.FINAL_ANSWER, request.getMessage(), request.getToolName(),
                null, null, request.getEmbeddingDim());
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
