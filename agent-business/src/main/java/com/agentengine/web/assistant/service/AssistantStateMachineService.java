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
                .state(LlmAgentState.IDLE)
                .createdAtEpochMs(now)
                .updatedAtEpochMs(now)
                .lastMessage(safe(request.getLastMessage()))
                .activeTurnCount(0)
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
        if (request.getIntent() != null) {
            current.setIntent(safe(request.getIntent()));
        }
        if (request.getSkillName() != null) {
            current.setSkillName(safe(request.getSkillName()));
        }
        current.setMissingSlots(request.getMissingSlots());
        current.setErrorMessage(safe(request.getErrorMessage()));
        if (request.getNeedClarification() != null) {
            current.setNeedClarification(request.getNeedClarification());
        }
        if (request.getClarificationType() != null) {
            current.setClarificationType(safe(request.getClarificationType()));
        }
        if (request.getClarificationQuestion() != null) {
            current.setClarificationQuestion(safe(request.getClarificationQuestion()));
        }
        if (request.getIntentCandidatesTop3() != null) {
            current.setIntentCandidatesTop3(request.getIntentCandidatesTop3());
        }
        if (request.getAssistantReply() != null) {
            current.setAssistantReply(safe(request.getAssistantReply()));
        }
        if (request.getActiveTurnCount() != null) {
            current.setActiveTurnCount(request.getActiveTurnCount());
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
        if (current == null) {
            if (next == LlmAgentState.IDLE || next == LlmAgentState.ACTIVE) {
                return;
            }
            throw new IllegalStateException("invalid transition: " + current + " -> " + next);
        }
        if ((current == LlmAgentState.IDLE || current == LlmAgentState.ACTIVE)
                && (next == LlmAgentState.IDLE || next == LlmAgentState.ACTIVE)) {
            return;
        }
        throw new IllegalStateException("invalid transition: " + current + " -> " + next);
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

    public void touch(AssistantUserState state) {
        if (state == null) {
            return;
        }
        if (!safe(state.getTaskId()).isBlank()) {
            stringRedisTemplate.expire(taskKey(state.getTaskId()), ttlMinutes, TimeUnit.MINUTES);
        }
        if (!safe(state.getUserId()).isBlank()) {
            stringRedisTemplate.expire(userKey(state.getUserId()), ttlMinutes, TimeUnit.MINUTES);
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
