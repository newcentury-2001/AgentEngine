package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import org.springframework.stereotype.Component;

@Component
public class FinalAnswerStateHandler extends AbstractAssistantStateHandler {

    public FinalAnswerStateHandler(AssistantStateMachineService stateMachineService) {
        super(stateMachineService);
    }

    @Override
    public LlmAgentState state() {
        return LlmAgentState.FINAL_ANSWER;
    }

    @Override
    public AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request) {
        if (notBlank(request.getErrorMessage())) {
            return move(current, LlmAgentState.FAILED, request.getMessage(), request.getToolName(),
                    request.getMissingSlots(), request.getErrorMessage(), request.getEmbeddingDim());
        }
        if (notBlank(request.getMessage())) {
            return move(current, LlmAgentState.INTENT_RECOGNITION, request.getMessage(), null,
                    null, null, request.getEmbeddingDim());
        }
        return move(current, LlmAgentState.FINAL_ANSWER, current.getLastMessage(), current.getLastToolName(),
                current.getMissingSlots(), current.getErrorMessage(), current.getLastEmbeddingDim());
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
