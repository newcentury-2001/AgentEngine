package com.agentengine.skill.embedding.controller;

import com.agentcommon.embedding.kafka.model.EmbeddingTaskMessage;
import com.agentengine.skill.embedding.kafka.EmbeddingTaskPublisher;
import com.agentengine.skill.embedding.kafka.EmbeddingTaskTracker;
import com.agentengine.skill.embedding.kafka.model.EmbeddingTaskStatusView;
import com.agentengine.skill.embedding.model.pojo.EmbeddingRequest;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.kafka.EmbeddingTaskTypes;
import com.agentengine.skill.embedding.service.EmbeddingOrchestrationService;
import com.agentcommon.mcp.parser.McpJsonParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingOrchestrationService orchestrationService;
    private final EmbeddingTaskPublisher embeddingTaskPublisher;
    private final EmbeddingTaskTracker embeddingTaskTracker;
    @Value("${agent.embedding.rocketmq.producer.max-batch-size:5}")
    private int maxBatchSize;

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateEmbeddings(
            @RequestBody EmbeddingRequest request) {

        log.info("Received tool embedding generation request for {} tools", request.getToolNames().size());

        return orchestrationService.generateToolEmbeddingsByNames(
                request.getToolNames(),
                request.isForceRegenerate()
        ).thenApply(ResponseEntity::ok).exceptionally(ex -> {
            log.error("Tool embedding failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(request.getToolNames().size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(request.getToolNames().size())
                    .failedItems(request.getToolNames())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message(buildSafeErrorMessage(ex))
                    .itemType("tool")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings(
            @RequestParam(defaultValue = "false") boolean forceRegenerate) {
        // jobId 作为整次全量任务的追踪键；前端查询 task-status 也用它。
        String jobId = UUID.randomUUID().toString().replace("-", "");
        long createdAt = System.currentTimeMillis();
        try {
            List<String> toolNames = orchestrationService.listPendingToolNames(forceRegenerate);
            if (toolNames.isEmpty()) {
                return CompletableFuture.completedFuture(ResponseEntity.ok(
                        EmbeddingResultExtended.builder()
                                .totalItems(0)
                                .embeddingSuccessCount(0)
                                .embeddingFailureCount(0)
                                .failedItems(new ArrayList<>())
                                .databaseSuccessCount(0)
                                .databaseFailureCount(0)
                                .databaseFailedItems(new ArrayList<>())
                                .embeddingTimeMs(0)
                                .databaseTimeMs(0)
                                .totalTimeMs(0)
                                .message("No pending tools to enqueue.")
                                .itemType("tool")
                                .build()
                ));
            }

            int oneBatchSize = Math.max(1, maxBatchSize);
            // 按固定大小切片，每条 RocketMQ 消息最多携带 N 个工具。
            List<List<String>> batches = splitBatches(toolNames, oneBatchSize);
            int totalBatches = batches.size();
            embeddingTaskTracker.markQueued(jobId, EmbeddingTaskTypes.TOOL_BATCH, createdAt, totalBatches);
            int maxRetry = embeddingTaskPublisher.maxRetry();
            for (int i = 0; i < totalBatches; i++) {
                EmbeddingTaskMessage msg = new EmbeddingTaskMessage();
                msg.setTaskId(jobId);
                msg.setJobId(jobId);
                msg.setTaskType(EmbeddingTaskTypes.TOOL_BATCH);
                msg.setForceRegenerate(forceRegenerate);
                msg.setToolNames(batches.get(i));
                msg.setBatchNo(i + 1);
                msg.setTotalBatches(totalBatches);
                msg.setRetryCount(0);
                msg.setMaxRetry(maxRetry);
                msg.setCreatedAtEpochMs(createdAt);
                // 主链路：同步发送到 broker，成功后才继续发送下一批。
                if (!embeddingTaskPublisher.sendWithRetry(msg)) {
                    embeddingTaskTracker.markFailed(jobId, "Failed to publish tool batch " + (i + 1), i + 1, totalBatches);
                    return CompletableFuture.completedFuture(ResponseEntity.internalServerError().body(
                            EmbeddingResultExtended.builder()
                                    .totalItems(toolNames.size())
                                    .embeddingSuccessCount(0)
                                    .embeddingFailureCount(toolNames.size())
                                    .failedItems(new ArrayList<>())
                                    .databaseSuccessCount(0)
                                    .databaseFailureCount(0)
                                    .databaseFailedItems(new ArrayList<>())
                                    .embeddingTimeMs(0)
                                    .databaseTimeMs(0)
                                    .totalTimeMs(0)
                                    .message("Embedding queue publish failed at batch " + (i + 1) + "/" + totalBatches)
                                    .itemType("tool")
                                    .build()
                    ));
                }
            }

            EmbeddingResultExtended accepted = EmbeddingResultExtended.builder()
                    .totalItems(toolNames.size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(0)
                    .failedItems(new ArrayList<>())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Embedding task accepted. taskId=" + jobId + ", type=TOOL_BATCH, batches=" + totalBatches)
                    .itemType("tool")
                    .build();
            return CompletableFuture.completedFuture(ResponseEntity.accepted().body(accepted));
        } catch (Exception e) {
            embeddingTaskTracker.markFailed(jobId, e.getMessage(), null, null);
            log.error("failed to enqueue tool embedding batches. jobId={}", jobId, e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().body(
                    EmbeddingResultExtended.builder()
                            .totalItems(0)
                            .embeddingSuccessCount(0)
                            .embeddingFailureCount(0)
                            .failedItems(new ArrayList<>())
                            .databaseSuccessCount(0)
                            .databaseFailureCount(0)
                            .databaseFailedItems(new ArrayList<>())
                            .embeddingTimeMs(0)
                            .databaseTimeMs(0)
                            .totalTimeMs(0)
                            .message("Failed to enqueue tool embedding task.")
                            .itemType("tool")
                            .build()
            ));
        }
    }

    @GetMapping("/task-status")
    public ResponseEntity<EmbeddingTaskStatusView> getTaskStatus(@RequestParam String taskId) {
        return embeddingTaskTracker.find(taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String buildSafeErrorMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage() == null ? "" : root.getMessage();
        if (msg.contains("Read timed out") || msg.contains("SocketTimeoutException")) {
            return "Embedding failed: database write timeout, please retry.";
        }
        if (msg.contains("ExecutorSaturatedException")) {
            return "Embedding failed: system busy, please retry later.";
        }
        if (msg.contains("DataAccess") || msg.contains("SQL")) {
            return "Embedding failed: database operation error.";
        }
        return "Embedding failed, please check server logs.";
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<EmbeddingStatusResponse>> getStatus(
            @RequestParam List<String> toolNames) {

        log.info("Received tool embedding status query for {} tools", toolNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                var skills = orchestrationService.loadSummarySkills();
                var toolMap = McpJsonParser.buildToolKeyMap(skills);

                List<String> existing = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                for (String toolName : toolNames) {
                    var tool = toolMap.get(toolName);
                    if (tool != null && tool.getEmbedding() != null) {
                        existing.add(toolName);
                    } else {
                        missing.add(toolName);
                    }
                }

                EmbeddingStatusResponse response = EmbeddingStatusResponse.builder()
                        .totalTools(toolNames.size())
                        .existingCount(existing.size())
                        .missingCount(missing.size())
                        .existingTools(existing)
                        .missingTools(missing)
                        .build();

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Failed to query tool embedding status", e);
                return ResponseEntity.internalServerError().build();
            }
        });
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingStatusResponse {
        private int totalTools;
        private int existingCount;
        private int missingCount;
        private List<String> existingTools;
        private List<String> missingTools;
    }

    // 将大列表拆成定长分片（默认最多 5 个），用于构建 MQ 子消息。
    private List<List<String>> splitBatches(List<String> items, int batchSize) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(items.size(), i + batchSize);
            out.add(new ArrayList<>(items.subList(i, end)));
        }
        return out;
    }
}
