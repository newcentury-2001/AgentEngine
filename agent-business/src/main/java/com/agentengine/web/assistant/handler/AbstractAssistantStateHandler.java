package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantStateTransitionRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractAssistantStateHandler implements AssistantStateHandler {

    protected final AssistantStateMachineService stateMachineService;

    protected AssistantUserState move(AssistantUserState current,
                                      LlmAgentState next,
                                      String message,
                                      String toolName,
                                      List<String> missingSlots,
                                      String errorMessage) {
        return move(current, next, message, toolName, missingSlots, errorMessage, null);
    }

    protected AssistantUserState move(AssistantUserState current,
                                      LlmAgentState next,
                                      String message,
                                      String toolName,
                                      List<String> missingSlots,
                                      String errorMessage,
                                      Integer embeddingDim) {
        AssistantStateTransitionRequest transition = new AssistantStateTransitionRequest();
        transition.setTaskId(current.getTaskId());
        transition.setUserId(current.getUserId());
        transition.setNextState(next);
        transition.setLastMessage(message);
        transition.setLastToolName(toolName);
        transition.setMissingSlots(missingSlots);
        transition.setErrorMessage(errorMessage);
        transition.setLastEmbeddingDim(embeddingDim);
        return stateMachineService.transition(transition);
    }
}
