package com.agentengine.web.assistant.service;

import com.agentengine.web.assistant.model.AssistantStateStartRequest;
import com.agentengine.web.assistant.model.AssistantStateTransitionRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantStateMachineService {

    private static final String TASK_KEY_PREFIX = "assistant:state:task:";
    private static final String USER_KEY_PREFIX = "assistant:state:user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.assistant.state.ttl-minutes:30}")
    private long ttlMinutes;

    public AssistantUserState start(AssistantStateStartRequest request) {
        String userId = safe(request.getUserId());
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        String taskId = safe(request.getTaskId());
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }

        String traceId = safe(request.getTraceId());
        if (traceId.isBlank()) {
            traceId = safe(MDC.get("traceId"));
        }
        if (traceId.isBlank()) {
            traceId = randomId();
        }

        long now = System.currentTimeMillis();
        AssistantUserState state = AssistantUserState.builder()
                .userId(userId)
                .taskId(taskId)
                .traceId(traceId)
                .state(LlmAgentState.INIT)
                .createdAtEpochMs(now)
                .updatedAtEpochMs(now)
                .lastMessage(safe(request.getLastMessage()))
                .build();

        save(state);
        return state;
    }

    public AssistantUserState transition(AssistantStateTransitionRequest request) {
        String taskId = safe(request.getTaskId());
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        LlmAgentState nextState = request.getNextState();
        if (nextState == null) {
            throw new IllegalArgumentException("nextState is required");
        }

        AssistantUserState current = findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
        String requestUserId = safe(request.getUserId());
        if (!requestUserId.isBlank() && !requestUserId.equals(current.getUserId())) {
            throw new IllegalArgumentException("userId mismatch for taskId=" + taskId);
        }
        validateTransition(current.getState(), nextState);

        current.setState(nextState);
        current.setUpdatedAtEpochMs(System.currentTimeMillis());
        String nextMessage = safe(request.getLastMessage());
        if (!nextMessage.isBlank()) {
            current.setLastMessage(nextMessage);
        }
        current.setLastToolName(safe(request.getLastToolName()));
        current.setMissingSlots(request.getMissingSlots());
        current.setErrorMessage(safe(request.getErrorMessage()));
        if (request.getLastEmbeddingDim() != null) {
            current.setLastEmbeddingDim(request.getLastEmbeddingDim());
        }
        save(current);
        return current;
    }

    public Optional<AssistantUserState> findByTaskId(String taskId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(taskKey(taskId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, AssistantUserState.class));
        } catch (Exception e) {
            log.warn("failed to load assistant state by taskId. taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    public Optional<AssistantUserState> findByUserId(String userId) {
        try {
            String taskId = stringRedisTemplate.opsForValue().get(userKey(userId));
            if (taskId == null || taskId.isBlank()) {
                return Optional.empty();
            }
            return findByTaskId(taskId);
        } catch (Exception e) {
            log.warn("failed to load assistant state by userId. userId={}", userId, e);
            return Optional.empty();
        }
    }

    private void validateTransition(LlmAgentState current, LlmAgentState next) {
        if (current == next) {
            return;
        }
        if (current == LlmAgentState.FAILED) {
            throw new IllegalStateException("terminal state cannot transition: " + current);
        }
        if (current == LlmAgentState.FINAL_ANSWER) {
            if (next == LlmAgentState.INTENT_RECOGNITION) {
                return;
            }
            throw new IllegalStateException("invalid transition: " + current + " -> " + next);
        }
        if (next == LlmAgentState.FAILED) {
            return;
        }
        if (current == null || current == LlmAgentState.INIT) {
            if (next == LlmAgentState.INTENT_RECOGNITION) {
                return;
            }
            throw new IllegalStateException("invalid transition: " + current + " -> " + next);
        }

        boolean allowed = switch (current) {
            case INTENT_RECOGNITION -> Set.of(
                    LlmAgentState.TOOL_EXECUTION,
                    LlmAgentState.SLOT_CLARIFICATION,
                    LlmAgentState.FINAL_ANSWER
            ).contains(next);
            case TOOL_EXECUTION -> Set.of(
                    LlmAgentState.SLOT_CLARIFICATION,
                    LlmAgentState.FINAL_ANSWER
            ).contains(next);
            case SLOT_CLARIFICATION -> Set.of(
                    LlmAgentState.TOOL_EXECUTION,
                    LlmAgentState.FINAL_ANSWER
            ).contains(next);
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("invalid transition: " + current + " -> " + next);
        }
    }

    private void save(AssistantUserState state) {
        try {
            String raw = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(taskKey(state.getTaskId()), raw, ttlMinutes, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(userKey(state.getUserId()), state.getTaskId(), ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to save assistant state. taskId={}, userId={}",
                    state.getTaskId(), state.getUserId(), e);
        }
    }

    private String taskKey(String taskId) {
        return TASK_KEY_PREFIX + taskId;
    }

    private String userKey(String userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
