package com.agentengine.web.assistant.service;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantStateStartRequest;
import com.agentengine.web.assistant.model.AssistantStateTransitionRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssistantAgentOrchestrationService {

    private final AssistantStateMachineService stateMachineService;

    /**
     * Main entry for assistant state flow.
     * Current simplified model has two states only: IDLE and ACTIVE.
     */
    public AssistantUserState execute(AssistantAgentProcessRequest request) {
        AssistantUserState current = resolveState(request);
        String message = safe(request.getMessage());

        if (current.getState() == null) {
            current = transition(current, LlmAgentState.IDLE, t -> {
                t.setLastMessage(message);
                t.setAssistantReply("");
            });
        }

        if (current.getState() == LlmAgentState.IDLE) {
            return executeIdle(current, request, message);
        }
        return executeActive(current, request, message);
    }

    private AssistantUserState executeIdle(AssistantUserState current,
                                           AssistantAgentProcessRequest request,
                                           String message) {
        String selectedIntent = safe(request.getSelectedIntent());
        if (selectedIntent.isBlank()) {
            return transition(current, LlmAgentState.IDLE, t -> {
                t.setLastMessage(message);
                t.setNeedClarification(true);
                t.setClarificationType("INTENT_REQUIRED");
                t.setClarificationQuestion("Please choose or provide your intent.");
                t.setAssistantReply("Please choose or provide your intent.");
                t.setErrorMessage("");
                t.setActiveTurnCount(0);
            });
        }

        return transition(current, LlmAgentState.ACTIVE, t -> {
            t.setLastMessage(message);
            t.setIntent(selectedIntent);
            t.setNeedClarification(false);
            t.setClarificationType("");
            t.setClarificationQuestion("");
            t.setMissingSlots(List.of());
            t.setErrorMessage("");
            t.setAssistantReply("Execution plan created, processing now.");
            t.setActiveTurnCount(0);
        });
    }

    private AssistantUserState executeActive(AssistantUserState current,
                                             AssistantAgentProcessRequest request,
                                             String message) {
        List<String> missingSlots = request.getMissingSlots() == null ? List.of() : request.getMissingSlots();
        boolean answerReady = Boolean.TRUE.equals(request.getAnswerReady());

        if (answerReady) {
            return transition(current, LlmAgentState.IDLE, t -> {
                t.setLastMessage(message);
                t.setIntent("");
                t.setSkillName("");
                t.setMissingSlots(List.of());
                t.setErrorMessage("");
                t.setNeedClarification(false);
                t.setClarificationType("");
                t.setClarificationQuestion("");
                t.setAssistantReply(safe(message).isBlank() ? "Done." : message);
                t.setActiveTurnCount(0);
            });
        }

        if (!missingSlots.isEmpty()) {
            return transition(current, LlmAgentState.ACTIVE, t -> {
                t.setLastMessage(message);
                t.setMissingSlots(missingSlots);
                t.setNeedClarification(false);
                t.setClarificationType("");
                t.setClarificationQuestion("");
                t.setAssistantReply("Missing required slots: " + String.join(", ", missingSlots) + ".");
                t.setErrorMessage("");
                Integer turns = current.getActiveTurnCount() == null ? 0 : current.getActiveTurnCount();
                t.setActiveTurnCount(turns + 1);
            });
        }

        return transition(current, LlmAgentState.ACTIVE, t -> {
            t.setLastMessage(message);
            t.setErrorMessage(safe(request.getErrorMessage()));
            t.setAssistantReply("Running.");
            Integer turns = current.getActiveTurnCount() == null ? 0 : current.getActiveTurnCount();
            t.setActiveTurnCount(turns + 1);
        });
    }

    private AssistantUserState resolveState(AssistantAgentProcessRequest request) {
        String taskId = safe(request.getTaskId());
        String userId = safe(request.getUserId());
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!taskId.isBlank()) {
            return stateMachineService.findByTaskId(taskId)
                    .orElseGet(() -> startState(userId, taskId, request));
        }
        return stateMachineService.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("taskId is required for first request"));
    }

    private AssistantUserState startState(String userId, String taskId, AssistantAgentProcessRequest request) {
        AssistantStateStartRequest start = new AssistantStateStartRequest();
        start.setUserId(userId);
        start.setTaskId(taskId);
        start.setTraceId(request.getTraceId());
        start.setLastMessage(request.getMessage());
        return stateMachineService.start(start);
    }

    private AssistantUserState transition(AssistantUserState current,
                                          LlmAgentState nextState,
                                          java.util.function.Consumer<AssistantStateTransitionRequest> updater) {
        AssistantStateTransitionRequest t = new AssistantStateTransitionRequest();
        t.setTaskId(current.getTaskId());
        t.setUserId(current.getUserId());
        t.setNextState(nextState);
        updater.accept(t);
        return stateMachineService.transition(t);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

