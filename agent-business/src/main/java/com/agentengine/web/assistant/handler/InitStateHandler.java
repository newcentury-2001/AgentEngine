package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import org.springframework.stereotype.Component;

@Component
public class InitStateHandler extends AbstractAssistantStateHandler {

    public InitStateHandler(AssistantStateMachineService stateMachineService) {
        super(stateMachineService);
    }

    @Override
    public LlmAgentState state() {
        return LlmAgentState.INIT;
    }

    @Override
    public AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request) {
        return move(
                current,
                LlmAgentState.INTENT_RECOGNITION,
                request.getMessage(),
                null,
                null,
                null,
                request.getEmbeddingDim()
        );
    }
}
