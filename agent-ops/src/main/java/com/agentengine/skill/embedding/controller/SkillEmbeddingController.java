package com.agentengine.skill.embedding.controller;

import com.agentcommon.embedding.kafka.model.EmbeddingTaskMessage;
import com.agentengine.skill.embedding.kafka.EmbeddingTaskPublisher;
import com.agentengine.skill.embedding.kafka.EmbeddingTaskTracker;
import com.agentengine.skill.embedding.kafka.model.EmbeddingTaskStatusView;
import com.agentengine.skill.embedding.model.pojo.SkillEmbeddingRequest;
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
@RequestMapping("/api/skill-embedding")
@RequiredArgsConstructor
public class SkillEmbeddingController {

    private final EmbeddingOrchestrationService orchestrationService;
    private final EmbeddingTaskPublisher embeddingTaskPublisher;
    private final EmbeddingTaskTracker embeddingTaskTracker;
    @Value("${agent.embedding.rocketmq.producer.max-batch-size:5}")
    private int maxBatchSize;

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateEmbeddings(
            @RequestBody SkillEmbeddingRequest request) {

        log.info("Received skill embedding generation request for {} skills (includeTools: {})",
                request.getSkillNames().size(), request.isIncludeTools());

        return orchestrationService.generateSkillEmbeddingsByNames(
                request.getSkillNames(),
                request.isForceRegenerate(),
                request.isIncludeTools()
        ).thenApply(ResponseEntity::ok).exceptionally(ex -> {
            log.error("Skill embedding failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(request.getSkillNames().size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(request.getSkillNames().size())
                    .failedItems(request.getSkillNames())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message(buildSafeErrorMessage(ex))
                    .itemType("skill")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings(
            @RequestParam(defaultValue = "true") boolean includeTools,
            @RequestParam(defaultValue = "false") boolean forceRegenerate) {

        // jobId 作为整次全量任务的追踪键；前端查询 task-status 也用它。
        String jobId = UUID.randomUUID().toString().replace("-", "");
        long createdAt = System.currentTimeMillis();
        try {
            List<String> skillNames = orchestrationService.listPendingSkillNames(forceRegenerate);
            if (skillNames.isEmpty()) {
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
                                .message("No pending skills to enqueue.")
                                .itemType("skill")
                                .build()
                ));
            }

            int oneBatchSize = Math.max(1, maxBatchSize);
            // 按固定大小切片，每条 RocketMQ 消息最多携带 N 个技能。
            List<List<String>> batches = splitBatches(skillNames, oneBatchSize);
            int totalBatches = batches.size();
            embeddingTaskTracker.markQueued(jobId, EmbeddingTaskTypes.SKILL_BATCH, createdAt, totalBatches);
            int maxRetry = embeddingTaskPublisher.maxRetry();
            for (int i = 0; i < totalBatches; i++) {
                EmbeddingTaskMessage msg = new EmbeddingTaskMessage();
                msg.setTaskId(jobId);
                msg.setJobId(jobId);
                msg.setTaskType(EmbeddingTaskTypes.SKILL_BATCH);
                msg.setIncludeTools(includeTools);
                msg.setForceRegenerate(forceRegenerate);
                msg.setSkillNames(batches.get(i));
                msg.setBatchNo(i + 1);
                msg.setTotalBatches(totalBatches);
                msg.setRetryCount(0);
                msg.setMaxRetry(maxRetry);
                msg.setCreatedAtEpochMs(createdAt);
                // 主链路：同步发送到 broker，成功后才继续发送下一批。
                if (!embeddingTaskPublisher.sendWithRetry(msg)) {
                    embeddingTaskTracker.markFailed(jobId, "Failed to publish skill batch " + (i + 1), i + 1, totalBatches);
                    return CompletableFuture.completedFuture(ResponseEntity.internalServerError().body(
                            EmbeddingResultExtended.builder()
                                    .totalItems(skillNames.size())
                                    .embeddingSuccessCount(0)
                                    .embeddingFailureCount(skillNames.size())
                                    .failedItems(new ArrayList<>())
                                    .databaseSuccessCount(0)
                                    .databaseFailureCount(0)
                                    .databaseFailedItems(new ArrayList<>())
                                    .embeddingTimeMs(0)
                                    .databaseTimeMs(0)
                                    .totalTimeMs(0)
                                    .message("Embedding queue publish failed at batch " + (i + 1) + "/" + totalBatches)
                                    .itemType("skill")
                                    .build()
                    ));
                }
            }
            EmbeddingResultExtended accepted = EmbeddingResultExtended.builder()
                    .totalItems(skillNames.size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(0)
                    .failedItems(new ArrayList<>())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Embedding task accepted. taskId=" + jobId + ", type=SKILL_BATCH, batches=" + totalBatches)
                    .itemType("skill")
                    .build();
            return CompletableFuture.completedFuture(ResponseEntity.accepted().body(accepted));
        } catch (Exception e) {
            embeddingTaskTracker.markFailed(jobId, e.getMessage(), null, null);
            log.error("failed to enqueue skill embedding batches. jobId={}", jobId, e);
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
                            .message("Failed to enqueue skill embedding task.")
                            .itemType("skill")
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
            return "Skill embedding failed: database write timeout, please retry.";
        }
        if (msg.contains("ExecutorSaturatedException")) {
            return "Skill embedding failed: system busy, please retry later.";
        }
        if (msg.contains("DataAccess") || msg.contains("SQL")) {
            return "Skill embedding failed: database operation error.";
        }
        return "Skill embedding failed, please check server logs.";
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<SkillEmbeddingStatusResponse>> getStatus(
            @RequestParam List<String> skillNames) {

        log.info("Received skill embedding status query for {} skills", skillNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                var skills = orchestrationService.loadSummarySkills();
                var skillMap = McpJsonParser.buildSkillNameMap(skills);

                List<String> existing = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                for (String skillName : skillNames) {
                    var skill = skillMap.get(skillName);
                    if (skill != null && skill.getEmbedding() != null) {
                        existing.add(skillName);
                    } else {
                        missing.add(skillName);
                    }
                }

                SkillEmbeddingStatusResponse response = SkillEmbeddingStatusResponse.builder()
                        .totalSkills(skillNames.size())
                        .existingCount(existing.size())
                        .missingCount(missing.size())
                        .existingSkills(existing)
                        .missingSkills(missing)
                        .build();

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Failed to query skill embedding status", e);
                return ResponseEntity.internalServerError().build();
            }
        });
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillEmbeddingStatusResponse {
        private int totalSkills;
        private int existingCount;
        private int missingCount;
        private List<String> existingSkills;
        private List<String> missingSkills;
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
