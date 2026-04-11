package com.agentengine.web.assistant.handler;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;

public interface AssistantStateHandler {
    LlmAgentState state();

    AssistantUserState handle(AssistantUserState current, AssistantAgentProcessRequest request);
}
