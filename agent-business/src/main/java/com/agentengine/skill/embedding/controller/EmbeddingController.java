package com.agentengine.skill.embedding.controller;

import com.agentengine.skill.embedding.model.pojo.EmbeddingRequest;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.service.EmbeddingOrchestrationService;
import com.agentcommon.mcp.parser.McpJsonParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/api/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingOrchestrationService orchestrationService;

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
                    .message("Error: " + ex.getMessage())
                    .itemType("tool")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings(
            @RequestParam(defaultValue = "false") boolean forceRegenerate) {
        log.info("Received request to generate embeddings for all tools (forceRegenerate: {})", forceRegenerate);

        return orchestrationService.generateAllToolEmbeddings(forceRegenerate)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("All tools embeddings generation failed", ex);
                    EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
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
                            .message("Error: " + ex.getMessage())
                            .itemType("tool")
                            .build();
                    return ResponseEntity.internalServerError().body(errorResult);
                });
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
}
