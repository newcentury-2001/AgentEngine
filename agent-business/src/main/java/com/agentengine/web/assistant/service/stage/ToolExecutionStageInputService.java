package com.agentengine.web.assistant.service.stage;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantInferenceResult;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantEntityMemoryService;
import com.agentengine.web.assistant.service.AssistantInferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolExecutionStageInputService implements AssistantStageInputService {

    private final AssistantInferenceService assistantInferenceService;
    private final AssistantEntityMemoryService assistantEntityMemoryService;

    @Override
    public LlmAgentState stage() {
        return LlmAgentState.TOOL_EXECUTION;
    }

    @Override
    public void prepare(AssistantUserState current, AssistantAgentProcessRequest request, String message) {
        AssistantInferenceResult inference = assistantInferenceService.inferForSlotFill(
                LlmAgentState.TOOL_EXECUTION,
                current.getMissingSlots(),
                message
        );
        merge(request, inference);
        assistantEntityMemoryService.merge(current.getTaskId(), inference.getEntityMemory());
    }

    private void merge(AssistantAgentProcessRequest request, AssistantInferenceResult inference) {
        request.setNeedTool(inference.isNeedTool());
        request.setAnswerReady(inference.isAnswerReady());
        request.setToolName(inference.getToolName());
        request.setMissingSlots(inference.getMissingSlots());
        request.setEmbeddingDim(inference.getEmbeddingDim());
        if (blank(request.getErrorMessage())) {
            request.setErrorMessage(inference.getErrorMessage());
        }
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
