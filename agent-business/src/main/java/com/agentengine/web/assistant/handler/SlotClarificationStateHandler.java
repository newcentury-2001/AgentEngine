package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import org.springframework.stereotype.Component;

@Component
public class SlotClarificationStateHandler extends AbstractAssistantStateHandler {

    public SlotClarificationStateHandler(AssistantStateMachineService stateMachineService) {
        super(stateMachineService);
    }

    @Override
    public LlmAgentState state() {
        return LlmAgentState.SLOT_CLARIFICATION;
    }

    @Override
    public AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request) {
        if (notBlank(request.getErrorMessage())) {
            return move(current, LlmAgentState.FAILED, null, request.getToolName(),
                    request.getMissingSlots(), request.getErrorMessage(), request.getEmbeddingDim());
        }
        if (request.getMissingSlots() != null && !request.getMissingSlots().isEmpty()) {
            return move(current, LlmAgentState.SLOT_CLARIFICATION, null, request.getToolName(),
                    request.getMissingSlots(), null, request.getEmbeddingDim());
        }
        return move(current, LlmAgentState.TOOL_EXECUTION, null, request.getToolName(),
                null, null, request.getEmbeddingDim());
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
