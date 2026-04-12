package com.agentengine.web.assistant.service.stage;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;

public interface AssistantStageInputService {
    AssistantStage stage();

    void prepare(AssistantUserState current, AssistantAgentProcessRequest request, String message);
}
