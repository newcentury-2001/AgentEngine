package com.agentengine.web.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantEntityMemoryService {

    private static final String ENTITY_KEY_PREFIX = "assistant:entity:";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${agent.assistant.state.ttl-minutes:30}")
    private long ttlMinutes;

    public void merge(String taskId, Map<String, String> entities) {
        if (blank(taskId) || entities == null || entities.isEmpty()) {
            return;
        }
        try {
            String key = key(taskId);
            entities.forEach((k, v) -> {
                if (!blank(k) && !blank(v)) {
                    stringRedisTemplate.opsForHash().put(key, k.trim(), v.trim());
                }
            });
            stringRedisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to merge entity memory. taskId={}", taskId, e);
        }
    }

    public Map<String, String> getAll(String taskId) {
        if (blank(taskId)) {
            return Map.of();
        }
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key(taskId));
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                String key = k == null ? "" : k.toString().trim();
                String value = v == null ? "" : v.toString().trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    result.put(key, value);
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("failed to load entity memory. taskId={}", taskId, e);
            return Map.of();
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
        return ENTITY_KEY_PREFIX + taskId;
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
