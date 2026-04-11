package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import org.springframework.stereotype.Component;

@Component
public class FailedStateHandler implements AssistantStateHandler {

    @Override
    public LlmAgentState state() {
        return LlmAgentState.FAILED;
    }

    @Override
    public AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request) {
        return current;
    }
}
