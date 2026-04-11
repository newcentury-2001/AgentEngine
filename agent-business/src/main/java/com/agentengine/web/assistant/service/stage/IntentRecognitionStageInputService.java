package com.agentengine.web.assistant.service.stage;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantInferenceResult;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantDialogueService;
import com.agentengine.web.assistant.service.AssistantEntityMemoryService;
import com.agentengine.web.assistant.service.AssistantInferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IntentRecognitionStageInputService implements AssistantStageInputService {

    private final AssistantDialogueService assistantDialogueService;
    private final AssistantInferenceService assistantInferenceService;
    private final AssistantEntityMemoryService assistantEntityMemoryService;

    @Override
    public LlmAgentState stage() {
        return LlmAgentState.INTENT_RECOGNITION;
    }

    @Override
    public void prepare(AssistantUserState current, AssistantAgentProcessRequest request, String message) {
        // 先取历史 2 条（不含当前），用于和当前输入拼成最多 3 条上下文。
        List<AssistantDialogueService.DialogueMessage> recentUserMessages =
                assistantDialogueService.recentUserMessages(current.getTaskId(), 2);
        // 用“历史 2 条用户消息 + 当前输入”做抽槽与 embedding，并回填到 request。
        AssistantInferenceResult inference = assistantInferenceService.inferForIntent(recentUserMessages, message);
        merge(request, inference);
        assistantEntityMemoryService.merge(current.getTaskId(), inference.getEntityMemory());
        // 推理完成后再写入当前消息，Redis 仍保持最近 2 条用户消息窗口。
        assistantDialogueService.appendUserMessage(current.getTaskId(), message);
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
