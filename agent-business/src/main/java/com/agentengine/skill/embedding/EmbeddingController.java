package com.agentengine.skill.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Embedding 生成控制器
 * 提供给前端的 REST API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingOrchestrationService orchestrationService;

    /**
     * 根据工具名称列表生成 embedding（包含入库和日志）
     *
     * @param request Embedding 请求
     * @return CompletableFuture 包含生成结果
     */
    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateEmbeddings(
            @RequestBody EmbeddingRequest request) {

        log.info("Received tool embedding generation request for {} tools", request.getToolNames().size());

        // 调用编排服务（包含入库和日志）
        return orchestrationService.generateEmbeddingsByNames(
                request.getToolNames(),
                request.isForceRegenerate()
        ).thenApply(result -> {
            log.info("Tool embedding completed: {}", result.getMessage());
            return ResponseEntity.ok(result);
        }).exceptionally(ex -> {
            log.error("Tool embedding failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(request.getToolNames().size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(request.getToolNames().size())
                    .failedItems(request.getToolNames())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new java.util.ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Error: " + ex.getMessage())
                    .itemType("tool")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    /**
     * 批量生成所有工具的 embedding（包含入库和日志）
     *
     * @return CompletableFuture 包含生成结果
     */
    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings() {

        log.info("Received request to generate embeddings for all tools");

        return orchestrationService.generateAllEmbeddings()
                .thenApply(result -> {
                    log.info("All tools embeddings generation completed: {}", result.getMessage());
                    return ResponseEntity.ok(result);
                }).exceptionally(ex -> {
            log.error("All tools embeddings generation failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(0)
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(0)
                    .failedItems(new java.util.ArrayList<>())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new java.util.ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Error: " + ex.getMessage())
                    .itemType("tool")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    /**
     * 查询指定工具的 embedding 状态
     *
     * @param toolNames 工具名称列表
     * @return CompletableFuture 包含状态信息
     */
    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<EmbeddingStatusResponse>> getStatus(
            @RequestParam List<String> toolNames) {

        log.info("Received tool embedding status query for {} tools", toolNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析 JSON 文件
                var skills = com.agentengine.skill.parser.McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
                var skillMap = skills.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.agentengine.skill.model.McpSkill::getSkillName,
                                s -> s,
                                (s1, s2) -> s1
                        ));

                List<String> existing = new java.util.ArrayList<>();
                List<String> missing = new java.util.ArrayList<>();

                for (String toolName : toolNames) {
                    var tool = skillMap.get(toolName);
                    if (tool != null && tool.getEmbedding() != null) {
                        existing.add(toolName);
                    } else if (tool == null) {
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

    /**
     * 工具 Embedding 状态响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EmbeddingStatusResponse {
        private int totalTools;
        private int existingCount;
        private int missingCount;
        private List<String> existingTools;
        private List<String> missingTools;
    }
}
