package com.agentops.mcpclean;

import com.agentops.mcpclean.model.McpSummaryCleanTaskMessage;
import com.agentops.service.McpSummaryLlmCleanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.mcp-cleaner.rocketmq.consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${agent.mcp-cleaner.rocketmq.consumer.topic:agent_mcp_summary_clean_tasks}",
        consumerGroup = "${agent.mcp-cleaner.rocketmq.consumer.group-id:agent-ops-mcp-cleaner-rmq}",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class McpSummaryCleanTaskConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final McpSummaryLlmCleanService cleanService;
    private final McpSummaryCleanTaskPublisher publisher;
    private final McpSummaryCleanTaskTracker taskTracker;
    private final StringRedisTemplate stringRedisTemplate;
    @Value("${agent.mcp-cleaner.idempotent.ttl-hours:48}")
    private long idempotentTtlHours;

    @Override
    public void onMessage(String payload) {
        McpSummaryCleanTaskMessage message = null;
        try {
            message = objectMapper.readValue(payload, McpSummaryCleanTaskMessage.class);
            if (message == null || message.getSkillName() == null || message.getSkillName().isBlank()) {
                return;
            }
            if (!tryMarkAttemptStarted(message)) {
                log.info("skip duplicated mcp clean attempt. taskId={}, skill={}, retry={}",
                        message.getTaskId(), message.getSkillName(), safeRetry(message));
                return;
            }
            taskTracker.markRunning(message.getTaskId(), message.getSkillName());

            McpSummaryLlmCleanService.TaskProcessResult result = cleanService.processTask(message);
            boolean hasRetryTools = result.retryToolNames() != null && !result.retryToolNames().isEmpty();
            boolean needRetry = hasRetryTools || result.retrySkill();
            if (!needRetry) {
                taskTracker.markSkillSucceeded(message.getTaskId(), message.getSkillName());
                tryFinalizeTask(message.getTaskId());
                return;
            }

            int currentRetry = message.getRetryCount() == null ? 0 : Math.max(0, message.getRetryCount());
            int maxRetry = message.getMaxRetry() == null ? publisher.maxRetry() : message.getMaxRetry();
            if (currentRetry >= Math.max(0, maxRetry)) {
                log.warn("mcp clean retry exceeded. taskId={}, skill={}, retry={}",
                        message.getTaskId(), message.getSkillName(), currentRetry);
                taskTracker.markSkillFailed(message.getTaskId(), message.getSkillName(), "retry exceeded");
                tryFinalizeTask(message.getTaskId());
                return;
            }

            McpSummaryCleanTaskMessage retry = new McpSummaryCleanTaskMessage();
            retry.setTaskId(message.getTaskId());
            retry.setSkillName(message.getSkillName());
            retry.setPendingToolNames(result.retryToolNames());
            retry.setSkillPending(result.retrySkill() || hasRetryTools);
            retry.setRetryCount(currentRetry + 1);
            retry.setMaxRetry(maxRetry);
            retry.setCreatedAtEpochMs(message.getCreatedAtEpochMs());

            boolean ok = publisher.sendWithDelayRetry(retry, retry.getRetryCount());
            if (!ok) {
                log.error("failed to publish delayed retry mcp clean task. taskId={}, skill={}, retry={}",
                        retry.getTaskId(), retry.getSkillName(), retry.getRetryCount());
                taskTracker.markSkillFailed(retry.getTaskId(), retry.getSkillName(), "publish delayed retry failed");
                tryFinalizeTask(retry.getTaskId());
                return;
            }
            taskTracker.markRetrying(
                    retry.getTaskId(),
                    retry.getSkillName(),
                    retry.getRetryCount(),
                    "partial retry scheduled"
            );
        } catch (Exception ex) {
            log.error("mcp summary clean consume failed. taskId={}",
                    message == null ? "-" : message.getTaskId(), ex);
            if (message != null) {
                scheduleRetry(message, "consume exception: " + ex.getClass().getSimpleName());
            }
        }
    }

    private void scheduleRetry(McpSummaryCleanTaskMessage message, String reason) {
        int currentRetry = safeRetry(message);
        int maxRetry = message.getMaxRetry() == null ? publisher.maxRetry() : message.getMaxRetry();
        if (currentRetry >= Math.max(0, maxRetry)) {
            taskTracker.markSkillFailed(message.getTaskId(), message.getSkillName(), "retry exceeded: " + reason);
            return;
        }
        McpSummaryCleanTaskMessage retry = new McpSummaryCleanTaskMessage();
        retry.setTaskId(message.getTaskId());
        retry.setSkillName(message.getSkillName());
        retry.setPendingToolNames(message.getPendingToolNames());
        retry.setSkillPending(message.getSkillPending());
        retry.setRetryCount(currentRetry + 1);
        retry.setMaxRetry(maxRetry);
        retry.setCreatedAtEpochMs(message.getCreatedAtEpochMs());
        boolean ok = publisher.sendWithDelayRetry(retry, retry.getRetryCount());
        if (ok) {
            taskTracker.markRetrying(retry.getTaskId(), retry.getSkillName(), retry.getRetryCount(), reason);
        } else {
            taskTracker.markSkillFailed(retry.getTaskId(), retry.getSkillName(), "publish delayed retry failed");
        }
    }

    private boolean tryMarkAttemptStarted(McpSummaryCleanTaskMessage message) {
        String key = idempotentAttemptKey(message);
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", Math.max(1, idempotentTtlHours), TimeUnit.HOURS
        );
        return Boolean.TRUE.equals(ok);
    }

    private int safeRetry(McpSummaryCleanTaskMessage message) {
        return message.getRetryCount() == null ? 0 : Math.max(0, message.getRetryCount());
    }

    private String idempotentAttemptKey(McpSummaryCleanTaskMessage message) {
        return "mcpclean:idem:" + safe(message.getTaskId())
                + ":" + safe(message.getSkillName())
                + ":" + safeRetry(message);
    }

    private String safe(String v) {
        return v == null ? "-" : v.trim();
    }

    private void tryFinalizeTask(String taskId) {
        try {
            taskTracker.find(taskId).ifPresent(view -> {
                int total = view.getTotalSkills() == null ? 0 : view.getTotalSkills();
                int processed = view.getProcessedSkills() == null ? 0 : view.getProcessedSkills();
                if (total > 0 && processed >= total) {
                    cleanService.finalizeTask(taskId);
                }
            });
        } catch (Exception e) {
            log.warn("failed to finalize mcp clean task. taskId={}", taskId, e);
        }
    }
}
