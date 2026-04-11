package com.agentengine.web.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    private String key(String taskId) {
        return ENTITY_KEY_PREFIX + taskId;
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
