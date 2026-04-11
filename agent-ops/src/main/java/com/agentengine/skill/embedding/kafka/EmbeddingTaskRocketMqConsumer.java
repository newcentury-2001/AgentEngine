package com.agentengine.skill.embedding.kafka;

import com.agentcommon.concurrent.ExecutorSaturatedException;
import com.agentcommon.embedding.kafka.model.EmbeddingTaskMessage;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.service.EmbeddingOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.embedding.rocketmq.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${agent.embedding.rocketmq.consumer.topic:agent_embedding_tasks}",
        consumerGroup = "${agent.embedding.rocketmq.consumer.group-id:agent-business-embedding-rmq}",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class EmbeddingTaskRocketMqConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final EmbeddingOrchestrationService orchestrationService;
    private final EmbeddingTaskTracker embeddingTaskTracker;
    private final EmbeddingTaskPublisher embeddingTaskPublisher;

    @Override
    public void onMessage(String payload) {
        EmbeddingTaskMessage message = null;
        try {
            // 1) 反序列化 MQ 消息；这里只接受 EmbeddingTaskMessage 结构。
            message = objectMapper.readValue(payload, EmbeddingTaskMessage.class);
            if (message == null || message.getTaskType() == null) {
                log.warn("Skip invalid embedding task payload");
                return;
            }
            // 2) 进入 RUNNING 状态，便于前端/运维看到任务已开始执行。
            String taskId = resolveTaskId(message);
            markRunning(taskId, message);
            // 3) 按 taskType 并行处理本条消息中的子项，聚合后更新任务状态。
            EmbeddingResultExtended result = processTask(message);
            embeddingTaskTracker.markSucceeded(taskId, result, message.getBatchNo(), message.getTotalBatches());
        } catch (Exception ex) {
            // 4) 失败统一走 handleFailure：可重试则回投延时消息，不可重试则标记失败。
            handleFailure(message, ex);
        }
    }

    private EmbeddingResultExtended processTask(EmbeddingTaskMessage message) {
        return switch (message.getTaskType()) {
            case EmbeddingTaskTypes.TOOL_BATCH -> processToolBatchParallel(message);
            case EmbeddingTaskTypes.SKILL_BATCH -> processSkillBatchParallel(message);
            default -> throw new IllegalArgumentException("Unknown embedding task type: " + message.getTaskType());
        };
    }

    private EmbeddingResultExtended processToolBatchParallel(EmbeddingTaskMessage message) {
        List<String> items = message.getToolNames() == null ? List.of() : message.getToolNames();
        if (items.isEmpty()) {
            return emptyResult("tool");
        }
        List<CompletableFuture<ItemOutcome>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(
                        () -> processSingleTool(item, message.isForceRegenerate())
                ))
                .toList();
        return aggregateOutcomes(message, "tool", futures);
    }

    private EmbeddingResultExtended processSkillBatchParallel(EmbeddingTaskMessage message) {
        List<String> items = message.getSkillNames() == null ? List.of() : message.getSkillNames();
        if (items.isEmpty()) {
            return emptyResult("skill");
        }
        boolean includeTools = Boolean.TRUE.equals(message.getIncludeTools());
        List<CompletableFuture<ItemOutcome>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(
                        () -> processSingleSkill(item, message.isForceRegenerate(), includeTools)
                ))
                .toList();
        return aggregateOutcomes(message, "skill", futures);
    }

    private ItemOutcome processSingleTool(String toolName, boolean forceRegenerate) {
        try {
            EmbeddingResultExtended result = orchestrationService
                    .generateToolEmbeddingsByNames(List.of(toolName), forceRegenerate)
                    .join();
            if (isSingleSuccess(result)) {
                return ItemOutcome.success(toolName);
            }
            return ItemOutcome.failed(toolName, safeMessage(result.getMessage()));
        } catch (Exception ex) {
            Throwable root = rootCause(ex);
            if (isRetryable(root)) {
                return ItemOutcome.retryable(toolName, safeMessage(root.getMessage()));
            }
            return ItemOutcome.failed(toolName, safeMessage(root.getMessage()));
        }
    }

    private ItemOutcome processSingleSkill(String skillName, boolean forceRegenerate, boolean includeTools) {
        try {
            EmbeddingResultExtended result = orchestrationService
                    .generateSkillEmbeddingsByNames(List.of(skillName), forceRegenerate, includeTools)
                    .join();
            if (isSingleSuccess(result)) {
                return ItemOutcome.success(skillName);
            }
            return ItemOutcome.failed(skillName, safeMessage(result.getMessage()));
        } catch (Exception ex) {
            Throwable root = rootCause(ex);
            if (isRetryable(root)) {
                return ItemOutcome.retryable(skillName, safeMessage(root.getMessage()));
            }
            return ItemOutcome.failed(skillName, safeMessage(root.getMessage()));
        }
    }

    private EmbeddingResultExtended aggregateOutcomes(
            EmbeddingTaskMessage message,
            String itemType,
            List<CompletableFuture<ItemOutcome>> futures) {
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<ItemOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();
        List<String> failedItems = new ArrayList<>();
        List<String> retryableItems = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        for (ItemOutcome one : outcomes) {
            if (one.status == ItemStatus.SUCCESS) {
                successCount++;
                continue;
            }
            failedCount++;
            if (one.status == ItemStatus.RETRYABLE) {
                retryableItems.add(one.name);
            }
            failedItems.add(one.name);
            if (!one.reason.isBlank()) {
                failedReasons.add(one.name + ":" + one.reason);
            }
        }

        if (!retryableItems.isEmpty() && canRetry(message)) {
            int nextRetry = currentRetry(message) + 1;
            EmbeddingTaskMessage retryMsg = buildRetrySubsetMessage(message, retryableItems, nextRetry);
            boolean delayedSent = embeddingTaskPublisher.sendWithDelayRetry(retryMsg, nextRetry);
            if (!delayedSent) {
                log.error("Failed to publish delayed retry subset. taskId={}, type={}, retry={}, items={}",
                        resolveTaskId(message), message.getTaskType(), nextRetry, retryableItems);
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("batch done: success=").append(successCount).append(", failed=").append(failedCount);
        if (!retryableItems.isEmpty()) {
            msg.append(", retryScheduled=").append(retryableItems.size());
        }
        if (!failedReasons.isEmpty()) {
            msg.append(", reasons=").append(String.join(" | ", failedReasons));
        }

        return EmbeddingResultExtended.builder()
                .totalItems(outcomes.size())
                .embeddingSuccessCount(successCount)
                .embeddingFailureCount(failedCount)
                .failedItems(failedItems)
                .databaseSuccessCount(successCount)
                .databaseFailureCount(failedCount)
                .databaseFailedItems(failedItems)
                .embeddingTimeMs(0)
                .databaseTimeMs(0)
                .totalTimeMs(0)
                .message(msg.toString())
                .itemType(itemType)
                .build();
    }

    private EmbeddingTaskMessage buildRetrySubsetMessage(
            EmbeddingTaskMessage src,
            List<String> retryableItems,
            int nextRetry) {
        EmbeddingTaskMessage retry = new EmbeddingTaskMessage();
        retry.setTaskId(src.getTaskId());
        retry.setJobId(src.getJobId());
        retry.setTaskType(src.getTaskType());
        retry.setForceRegenerate(src.isForceRegenerate());
        retry.setIncludeTools(src.getIncludeTools());
        retry.setBatchNo(src.getBatchNo());
        retry.setTotalBatches(src.getTotalBatches());
        retry.setRetryCount(nextRetry);
        retry.setMaxRetry(src.getMaxRetry());
        retry.setCreatedAtEpochMs(src.getCreatedAtEpochMs());
        if (EmbeddingTaskTypes.TOOL_BATCH.equals(src.getTaskType())) {
            retry.setToolNames(retryableItems);
        } else {
            retry.setSkillNames(retryableItems);
        }
        return retry;
    }

    private EmbeddingResultExtended emptyResult(String itemType) {
        return EmbeddingResultExtended.builder()
                .totalItems(0)
                .embeddingSuccessCount(0)
                .embeddingFailureCount(0)
                .failedItems(List.of())
                .databaseSuccessCount(0)
                .databaseFailureCount(0)
                .databaseFailedItems(List.of())
                .embeddingTimeMs(0)
                .databaseTimeMs(0)
                .totalTimeMs(0)
                .message("empty batch")
                .itemType(itemType)
                .build();
    }

    private boolean isSingleSuccess(EmbeddingResultExtended result) {
        return result != null
                && result.getEmbeddingSuccessCount() > 0
                && result.getDatabaseSuccessCount() > 0;
    }

    private void handleFailure(EmbeddingTaskMessage message, Exception ex) {
        String taskId = message == null ? "-" : resolveTaskId(message);
        if (message != null && isRetryable(ex) && canRetry(message)) {
            int nextRetry = currentRetry(message) + 1;
            message.setRetryCount(nextRetry);
            // 可重试异常走延时重投；发送成功后直接返回，不阻塞消费线程。
            boolean delayedSent = embeddingTaskPublisher.sendWithDelayRetry(message, nextRetry);
            if (delayedSent) {
                log.warn(
                        "Embedding task moved to delayed retry. taskId={}, type={}, retry={}",
                        taskId, message.getTaskType(), nextRetry, ex
                );
                return;
            }
            log.error("Failed to publish delayed retry message. taskId={}, retry={}", taskId, nextRetry, ex);
        }
        Integer batchNo = message == null ? null : message.getBatchNo();
        Integer totalBatches = message == null ? null : message.getTotalBatches();
        embeddingTaskTracker.markFailed(taskId, ex.getMessage(), batchNo, totalBatches);
        log.error("Embedding task failed. taskId={}", taskId, ex);
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ExecutorSaturatedException || current instanceof RejectedExecutionException) {
                return true;
            }
            if (current instanceof CompletionException && current.getCause() != null) {
                current = current.getCause();
            } else {
                current = current.getCause();
            }
        }
        return false;
    }

    private void markRunning(String taskId, EmbeddingTaskMessage message) {
        Integer batchNo = message == null ? null : message.getBatchNo();
        Integer totalBatches = message == null ? null : message.getTotalBatches();
        embeddingTaskTracker.markRunning(taskId, batchNo, totalBatches);
    }

    private String resolveTaskId(EmbeddingTaskMessage message) {
        if (message.getTaskId() != null && !message.getTaskId().isBlank()) {
            return message.getTaskId();
        }
        if (message.getJobId() != null && !message.getJobId().isBlank()) {
            return message.getJobId();
        }
        return "-";
    }

    private boolean canRetry(EmbeddingTaskMessage message) {
        int current = currentRetry(message);
        int maxRetry = message.getMaxRetry() == null ? embeddingTaskPublisher.maxRetry() : message.getMaxRetry();
        // 达到最大重试次数后不再回投延时队列，按失败结束。
        return current < Math.max(0, maxRetry);
    }

    private int currentRetry(EmbeddingTaskMessage message) {
        return message.getRetryCount() == null ? 0 : Math.max(0, message.getRetryCount());
    }

    private Throwable rootCause(Throwable ex) {
        Throwable cur = ex;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur == null ? ex : cur;
    }

    private String safeMessage(String raw) {
        return (raw == null || raw.isBlank()) ? "-" : raw;
    }

    private enum ItemStatus {
        SUCCESS,
        RETRYABLE,
        FAILED
    }

    private static final class ItemOutcome {
        private final String name;
        private final ItemStatus status;
        private final String reason;

        private ItemOutcome(String name, ItemStatus status, String reason) {
            this.name = name;
            this.status = status;
            this.reason = reason == null ? "-" : reason;
        }

        private static ItemOutcome success(String name) {
            return new ItemOutcome(name, ItemStatus.SUCCESS, "-");
        }

        private static ItemOutcome retryable(String name, String reason) {
            return new ItemOutcome(name, ItemStatus.RETRYABLE, reason);
        }

        private static ItemOutcome failed(String name, String reason) {
            return new ItemOutcome(name, ItemStatus.FAILED, reason);
        }
    }
}
