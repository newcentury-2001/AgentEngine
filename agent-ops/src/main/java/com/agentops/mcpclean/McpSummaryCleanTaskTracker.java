package com.agentops.mcpclean;

import com.agentops.mcpclean.model.McpCleanTaskState;
import com.agentops.mcpclean.model.McpSummaryCleanTaskStatusView;
import com.agentops.mcpclean.websocket.McpSummaryCleanTaskWebSocketBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpSummaryCleanTaskTracker {

    private static final String STATUS_KEY_PREFIX = "mcpclean:task:status:";
    private static final String PROCESSED_SET_PREFIX = "mcpclean:task:processed:";
    private static final String FAILED_SET_PREFIX = "mcpclean:task:failed:";
    private static final String SUCCESS_SET_PREFIX = "mcpclean:task:success:";
    private static final long TASK_TTL_MINUTES = 60L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final McpSummaryCleanTaskWebSocketBroadcaster broadcaster;

    public void markQueued(String taskId, int totalSkills, long createdAtEpochMs) {
        McpSummaryCleanTaskStatusView view = McpSummaryCleanTaskStatusView.builder()
                .taskId(taskId)
                .state(McpCleanTaskState.QUEUED)
                .totalSkills(Math.max(0, totalSkills))
                .processedSkills(0)
                .successSkills(0)
                .failedSkills(0)
                .createdAtEpochMs(createdAtEpochMs)
                .updatedAtEpochMs(System.currentTimeMillis())
                .build();
        save(view);
        broadcaster.publish(view);
    }

    public void markRunning(String taskId, String currentSkill) {
        McpSummaryCleanTaskStatusView view = find(taskId).orElse(newEmpty(taskId));
        if (view.getState() == McpCleanTaskState.QUEUED) {
            view.setState(McpCleanTaskState.RUNNING);
        }
        view.setCurrentSkill(currentSkill);
        view.setUpdatedAtEpochMs(System.currentTimeMillis());
        refreshCounters(view);
        save(view);
        broadcaster.publish(view);
    }

    public void markRetrying(String taskId, String currentSkill, int retryCount, String reason) {
        McpSummaryCleanTaskStatusView view = find(taskId).orElse(newEmpty(taskId));
        if (view.getState() == McpCleanTaskState.QUEUED) {
            view.setState(McpCleanTaskState.RUNNING);
        }
        view.setCurrentSkill(currentSkill);
        view.setCurrentRetryCount(retryCount);
        view.setLastError(reason);
        view.setUpdatedAtEpochMs(System.currentTimeMillis());
        refreshCounters(view);
        save(view);
        broadcaster.publish(view);
    }

    public void markSkillSucceeded(String taskId, String skillName) {
        try {
            stringRedisTemplate.opsForSet().add(PROCESSED_SET_PREFIX + taskId, safe(skillName));
            stringRedisTemplate.opsForSet().add(SUCCESS_SET_PREFIX + taskId, safe(skillName));
            expireTaskAuxKeys(taskId);
        } catch (Exception e) {
            log.warn("failed to mark mcp clean success set. taskId={}, skill={}", taskId, skillName, e);
        }

        McpSummaryCleanTaskStatusView view = find(taskId).orElse(newEmpty(taskId));
        view.setCurrentSkill(skillName);
        view.setCurrentRetryCount(0);
        view.setLastError(null);
        view.setUpdatedAtEpochMs(System.currentTimeMillis());
        refreshCounters(view);
        closeIfCompleted(view);
        save(view);
        broadcaster.publish(view);
    }

    public void markSkillFailed(String taskId, String skillName, String reason) {
        try {
            stringRedisTemplate.opsForSet().add(PROCESSED_SET_PREFIX + taskId, safe(skillName));
            stringRedisTemplate.opsForSet().add(FAILED_SET_PREFIX + taskId, safe(skillName));
            expireTaskAuxKeys(taskId);
        } catch (Exception e) {
            log.warn("failed to mark mcp clean failed set. taskId={}, skill={}", taskId, skillName, e);
        }

        McpSummaryCleanTaskStatusView view = find(taskId).orElse(newEmpty(taskId));
        view.setCurrentSkill(skillName);
        view.setLastError(reason);
        view.setUpdatedAtEpochMs(System.currentTimeMillis());
        refreshCounters(view);
        closeIfCompleted(view);
        save(view);
        broadcaster.publish(view);
    }

    public Optional<McpSummaryCleanTaskStatusView> find(String taskId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(statusKey(taskId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            McpSummaryCleanTaskStatusView view = objectMapper.readValue(raw, McpSummaryCleanTaskStatusView.class);
            refreshCounters(view);
            return Optional.of(view);
        } catch (Exception e) {
            log.warn("failed to load mcp clean task status from redis. taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    private void closeIfCompleted(McpSummaryCleanTaskStatusView view) {
        int total = safeInt(view.getTotalSkills());
        int processed = safeInt(view.getProcessedSkills());
        if (total <= 0 || processed < total) {
            if (view.getState() == McpCleanTaskState.QUEUED) {
                view.setState(McpCleanTaskState.RUNNING);
            }
            return;
        }
        view.setFinishedAtEpochMs(System.currentTimeMillis());
        int failed = safeInt(view.getFailedSkills());
        view.setState(failed > 0 ? McpCleanTaskState.PARTIAL_FAILED : McpCleanTaskState.SUCCEEDED);
    }

    private void refreshCounters(McpSummaryCleanTaskStatusView view) {
        String taskId = safe(view.getTaskId());
        if (taskId.isBlank()) {
            return;
        }
        Long processed = stringRedisTemplate.opsForSet().size(PROCESSED_SET_PREFIX + taskId);
        Long success = stringRedisTemplate.opsForSet().size(SUCCESS_SET_PREFIX + taskId);
        Long failed = stringRedisTemplate.opsForSet().size(FAILED_SET_PREFIX + taskId);
        view.setProcessedSkills((int) (processed == null ? 0 : processed));
        view.setSuccessSkills((int) (success == null ? 0 : success));
        view.setFailedSkills((int) (failed == null ? 0 : failed));
    }

    private void save(McpSummaryCleanTaskStatusView view) {
        try {
            stringRedisTemplate.opsForValue().set(
                    statusKey(view.getTaskId()),
                    objectMapper.writeValueAsString(view),
                    TASK_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            expireTaskAuxKeys(view.getTaskId());
        } catch (Exception e) {
            log.warn("failed to save mcp clean task status. taskId={}", view.getTaskId(), e);
        }
    }

    private void expireTaskAuxKeys(String taskId) {
        stringRedisTemplate.expire(PROCESSED_SET_PREFIX + taskId, TASK_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.expire(SUCCESS_SET_PREFIX + taskId, TASK_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.expire(FAILED_SET_PREFIX + taskId, TASK_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private McpSummaryCleanTaskStatusView newEmpty(String taskId) {
        return McpSummaryCleanTaskStatusView.builder()
                .taskId(taskId)
                .state(McpCleanTaskState.RUNNING)
                .totalSkills(0)
                .processedSkills(0)
                .successSkills(0)
                .failedSkills(0)
                .createdAtEpochMs(System.currentTimeMillis())
                .updatedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private String statusKey(String taskId) {
        return STATUS_KEY_PREFIX + taskId;
    }
}

