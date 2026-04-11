package com.agentengine.skill.embedding.kafka;

import com.agentengine.skill.embedding.kafka.model.EmbeddingTaskState;
import com.agentengine.skill.embedding.kafka.model.EmbeddingTaskStatusView;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.websocket.EmbeddingTaskWebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingTaskTracker {

    private static final String TASK_KEY_PREFIX = "embed:task:";
    private static final long TASK_TTL_MINUTES = 5L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingTaskWebSocketBroadcaster broadcaster;

    // 标记任务已入队并广播状态。
    public void markQueued(String taskId, String taskType, long createdAtEpochMs, Integer totalBatches) {
        EmbeddingTaskStatusView view = EmbeddingTaskStatusView.builder()
                .taskId(taskId)
                .taskType(taskType)
                .state(EmbeddingTaskState.QUEUED)
                .totalBatches(totalBatches)
                .createdAtEpochMs(createdAtEpochMs)
                .build();
        save(view);
        broadcaster.publish(view);
    }

    // 标记任务开始执行（RUNNING）并刷新开始时间。
    public void markRunning(String taskId, Integer currentBatchNo, Integer totalBatches) {
        EmbeddingTaskStatusView view = find(taskId)
                .orElse(EmbeddingTaskStatusView.builder()
                        .taskId(taskId)
                        .taskType("-")
                        .createdAtEpochMs(System.currentTimeMillis())
                        .build());
        view.setState(EmbeddingTaskState.RUNNING);
        view.setCurrentBatchNo(currentBatchNo);
        if (totalBatches != null) {
            view.setTotalBatches(totalBatches);
        }
        view.setStartedAtEpochMs(System.currentTimeMillis());
        save(view);
        broadcaster.publish(view);
    }

    // 标记任务执行成功并保存结果快照。
    public void markSucceeded(String taskId, EmbeddingResultExtended result, Integer currentBatchNo, Integer totalBatches) {
        EmbeddingTaskStatusView view = find(taskId)
                .orElse(EmbeddingTaskStatusView.builder()
                        .taskId(taskId)
                        .taskType("-")
                        .createdAtEpochMs(System.currentTimeMillis())
                        .build());
        view.setState(EmbeddingTaskState.SUCCEEDED);
        view.setCurrentBatchNo(currentBatchNo);
        if (totalBatches != null) {
            view.setTotalBatches(totalBatches);
        }
        view.setFinishedAtEpochMs(System.currentTimeMillis());
        view.setResult(result);
        view.setErrorMessage(null);
        save(view);
        broadcaster.publish(view);
    }

    // 标记任务执行失败并记录错误信息。
    public void markFailed(String taskId, String errorMessage, Integer currentBatchNo, Integer totalBatches) {
        EmbeddingTaskStatusView view = find(taskId)
                .orElse(EmbeddingTaskStatusView.builder()
                        .taskId(taskId)
                        .taskType("-")
                        .createdAtEpochMs(System.currentTimeMillis())
                        .build());
        view.setState(EmbeddingTaskState.FAILED);
        view.setCurrentBatchNo(currentBatchNo);
        if (totalBatches != null) {
            view.setTotalBatches(totalBatches);
        }
        view.setFinishedAtEpochMs(System.currentTimeMillis());
        view.setErrorMessage(errorMessage);
        save(view);
        broadcaster.publish(view);
    }

    // 按 taskId 查询当前任务状态（可能为空）。
    public Optional<EmbeddingTaskStatusView> find(String taskId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(taskKey(taskId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, EmbeddingTaskStatusView.class));
        } catch (Exception e) {
            log.warn("failed to load task status from redis. taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    // 将任务状态写入 Redis，并设置过期时间。
    private void save(EmbeddingTaskStatusView view) {
        try {
            String raw = objectMapper.writeValueAsString(view);
            stringRedisTemplate.opsForValue().set(
                    taskKey(view.getTaskId()),
                    raw,
                    TASK_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("failed to save task status to redis. taskId={}", view.getTaskId(), e);
        }
    }

    // 生成任务状态在 Redis 中的键名。
    private String taskKey(String taskId) {
        return TASK_KEY_PREFIX + taskId;
    }
}
