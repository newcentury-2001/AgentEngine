package com.agentengine.web.assistant.service;

import com.agentengine.web.assistant.model.AssistantExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantPlanService {

    private static final String PLAN_KEY_PREFIX = "assistant:plan:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.assistant.state.ttl-minutes:30}")
    private long ttlMinutes;

    public void save(AssistantExecutionPlan plan) {
        if (plan == null || blank(plan.getTaskId())) {
            return;
        }
        try {
            String raw = objectMapper.writeValueAsString(plan);
            stringRedisTemplate.opsForValue().set(key(plan.getTaskId()), raw, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to save assistant plan. taskId={}", plan.getTaskId(), e);
        }
    }

    public Optional<AssistantExecutionPlan> findByTaskId(String taskId) {
        if (blank(taskId)) {
            return Optional.empty();
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(key(taskId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, AssistantExecutionPlan.class));
        } catch (Exception e) {
            log.warn("failed to load assistant plan. taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    public void clear(String taskId) {
        if (blank(taskId)) {
            return;
        }
        stringRedisTemplate.delete(key(taskId));
    }

    public void touch(String taskId) {
        if (blank(taskId)) {
            return;
        }
        stringRedisTemplate.expire(key(taskId), ttlMinutes, TimeUnit.MINUTES);
    }

    private String key(String taskId) {
        return PLAN_KEY_PREFIX + taskId;
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
