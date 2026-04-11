package com.agentengine.web.assistant.service;

import com.agentengine.web.assistant.handler.AssistantStateHandler;
import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantStateStartRequest;
import com.agentengine.web.assistant.model.AssistantStateTransitionRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.stage.AssistantStageInputService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AssistantAgentOrchestrationService {

    private final AssistantStateMachineService stateMachineService;
    private final Map<LlmAgentState, AssistantStateHandler> handlers = new EnumMap<>(LlmAgentState.class);
    private final Map<LlmAgentState, AssistantStageInputService> stageInputServices = new EnumMap<>(LlmAgentState.class);

    public AssistantAgentOrchestrationService(AssistantStateMachineService stateMachineService,
                                              List<AssistantStateHandler> stateHandlers,
                                              List<AssistantStageInputService> stageServices) {
        this.stateMachineService = stateMachineService;
        for (AssistantStateHandler handler : stateHandlers) {
            handlers.put(handler.state(), handler);
        }
        for (AssistantStageInputService service : stageServices) {
            stageInputServices.put(service.stage(), service);
        }
    }

    public AssistantUserState execute(AssistantAgentProcessRequest request) {
        AssistantUserState current = resolveState(request);
        String message = safe(request.getMessage());
        if (!message.isBlank()) {
            if (current.getState() == LlmAgentState.FINAL_ANSWER) {
                current = toIntentRecognition(current, message, null);
            }
            if (current.getState() == LlmAgentState.INIT) {
                current = toIntentRecognition(current, message, null);
            }
            AssistantStageInputService stageService = stageInputServices.get(current.getState());
            if (stageService != null) {
                // 按阶段补齐信号（抽槽结果/向量维度等）到 request，供后续状态处理器使用。
                stageService.prepare(current, request, message);
            }
            // 保留用户原始输入，供后续状态迁移和审计字段使用。
            request.setMessage(message);
        }

        AssistantStateHandler handler = handlers.get(current.getState());
        if (handler == null) {
            throw new IllegalStateException("no handler found for state: " + current.getState());
        }
        log.debug("assistant execute. userId={}, taskId={}, currentState={}",
                current.getUserId(), current.getTaskId(), current.getState());
        return handler.handle(current, request);
    }

    private AssistantUserState resolveState(AssistantAgentProcessRequest request) {
        String taskId = safe(request.getTaskId());
        String userId = safe(request.getUserId());
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!taskId.isBlank()) {
            return stateMachineService.findByTaskId(taskId)
                    .orElseGet(() -> {
                        AssistantUserState started = startState(userId, taskId, request);
                        return toIntentRecognition(started, request.getMessage(), null);
                    });
        }
        return stateMachineService.findByUserId(userId)
                .orElseGet(() -> {
                    if (taskId.isBlank()) {
                        throw new IllegalArgumentException("taskId is required for first request");
                    }
                    AssistantUserState started = startState(userId, taskId, request);
                    return toIntentRecognition(started, request.getMessage(), null);
                });
    }

    private AssistantUserState startState(String userId, String taskId, AssistantAgentProcessRequest request) {
        AssistantStateStartRequest start = new AssistantStateStartRequest();
        start.setUserId(userId);
        start.setTaskId(taskId);
        start.setTraceId(request.getTraceId());
        start.setLastMessage(request.getMessage());
        return stateMachineService.start(start);
    }

    private AssistantUserState toIntentRecognition(AssistantUserState current, String message, Integer embeddingDim) {
        return stateMachineService.transition(buildTransition(
                current,
                LlmAgentState.INTENT_RECOGNITION,
                message,
                null,
                null,
                null,
                embeddingDim
        ));
    }

    private AssistantStateTransitionRequest buildTransition(
            AssistantUserState current,
            LlmAgentState nextState,
            String message,
            String toolName,
            List<String> missingSlots,
            String errorMessage,
            Integer embeddingDim) {
        AssistantStateTransitionRequest transition = new AssistantStateTransitionRequest();
        transition.setTaskId(current.getTaskId());
        transition.setUserId(current.getUserId());
        transition.setNextState(nextState);
        transition.setLastMessage(message);
        transition.setLastToolName(toolName);
        transition.setMissingSlots(missingSlots);
        transition.setErrorMessage(errorMessage);
        transition.setLastEmbeddingDim(embeddingDim);
        return transition;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
