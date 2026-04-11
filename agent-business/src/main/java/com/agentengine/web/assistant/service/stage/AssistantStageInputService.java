package com.agentengine.web.assistant.service.stage;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;

public interface AssistantStageInputService {
    LlmAgentState stage();

    void prepare(AssistantUserState current, AssistantAgentProcessRequest request, String message);
}
