package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.parser.McpJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Embedding 编排服务
 * 负责协调 embedding 生成、数据库入库、日志记录三个操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingOrchestrationService {

    private final EmbeddingResource embeddingResource;
    private final EmbeddingDbRepository dbRepository;
    private final EmbeddingLogFileService logFileService;
    @Qualifier("pgIoExecutor")
    private final ExecutorService pgIoExecutor;

    /**
     * 根据工具名称列表生成 embedding（包含入库和日志）
     */
    public CompletableFuture<EmbeddingResultExtended> generateEmbeddingsByNames(
            List<String> toolNames,
            boolean forceRegenerate) {

        long embeddingStartTime = System.currentTimeMillis();
        log.info("Starting tool embedding generation for {} tools", toolNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
                Map<String, McpTool> allTools = McpJsonParser.buildToolKeyMap(skills);
                List<McpTool> targetTools = filterToolsByNames(toolNames, allTools, forceRegenerate);

                if (targetTools.isEmpty()) {
                    log.warn("No matching tools found");
                    return EmbeddingResultExtended.builder()
                            .totalItems(toolNames.size())
                            .embeddingSuccessCount(0)
                            .embeddingFailureCount(toolNames.size())
                            .failedItems(toolNames)
                            .databaseSuccessCount(0)
                            .databaseFailureCount(0)
                            .databaseFailedItems(new ArrayList<>())
                            .embeddingTimeMs(System.currentTimeMillis() - embeddingStartTime)
                            .databaseTimeMs(0)
                            .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                            .message("No matching tools found")
                            .itemType("tool")
                            .build();
                }

                log.info("Found {} tools to process", targetTools.size());

                CompletableFuture<List<McpTool>> embeddingFuture = embeddingResource.generateBatchEmbeddingsAsync(targetTools);

                return embeddingFuture.thenCompose(processedTools -> {
                    long embeddingTimeMs = System.currentTimeMillis() - embeddingStartTime;
                    int embeddingSuccessCount = (int) processedTools.stream()
                            .filter(t -> t.getEmbedding() != null)
                            .count();
                    int embeddingFailureCount = processedTools.size() - embeddingSuccessCount;

                    CompletableFuture<int[]> dbFuture = CompletableFuture.supplyAsync(
                            () -> dbRepository.batchSaveToolEmbeddings(processedTools),
                            pgIoExecutor
                    );

                    CompletableFuture<Void> logFuture = logFileService.writeLogAsync(
                            EmbeddingResultExtended.builder()
                                    .totalItems(targetTools.size())
                                    .embeddingSuccessCount(embeddingSuccessCount)
                                    .embeddingFailureCount(embeddingFailureCount)
                                    .failedItems(processedTools.stream()
                                            .filter(t -> t.getEmbedding() == null)
                                            .map(t -> t.getSkillName() + ":" + t.getToolName())
                                            .toList())
                                    .embeddingTimeMs(embeddingTimeMs)
                                    .databaseTimeMs(0)
                                    .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                                    .itemType("tool")
                                    .build(),
                            pgIoExecutor
                    );

                    return CompletableFuture.allOf(dbFuture, logFuture)
                            .thenCompose(v -> {
                                long dbTimeMs = System.currentTimeMillis() - embeddingStartTime - embeddingTimeMs;
                                int[] dbResult = dbFuture.join();
                                int dbSuccessCount = countSuccess(dbResult);
                                int dbFailureCount = dbResult.length - dbSuccessCount;

                                List<String> dbFailedItems = new ArrayList<>();
                                int successIndex = 0;
                                for (McpTool tool : processedTools) {
                                    if (tool.getEmbedding() != null) {
                                        if (dbResult[successIndex] > 0) {
                                            successIndex++;
                                        } else {
                                            dbFailedItems.add(tool.getSkillName() + ":" + tool.getToolName());
                                        }
                                    }
                                }

                                return CompletableFuture.completedFuture(EmbeddingResultExtended.builder()
                                        .totalItems(targetTools.size())
                                        .embeddingSuccessCount(embeddingSuccessCount)
                                        .embeddingFailureCount(embeddingFailureCount)
                                        .failedItems(processedTools.stream()
                                                .filter(t -> t.getEmbedding() == null)
                                                .map(t -> t.getSkillName() + ":" + t.getToolName())
                                                .toList())
                                        .databaseSuccessCount(dbSuccessCount)
                                        .databaseFailureCount(dbFailureCount)
                                        .databaseFailedItems(dbFailedItems)
                                        .embeddingTimeMs(embeddingTimeMs)
                                        .databaseTimeMs(dbTimeMs)
                                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                                        .message(String.format("Tool embedding completed. Embedding: %d/%d, Database: %d/%d",
                                                embeddingSuccessCount, targetTools.size(),
                                                dbSuccessCount, embeddingSuccessCount))
                                        .itemType("tool")
                                        .build());
                            });
                });

            } catch (IOException e) {
                log.error("Failed to parse JSON file", e);
                return EmbeddingResultExtended.builder()
                        .totalItems(toolNames.size())
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(toolNames.size())
                        .failedItems(toolNames)
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .message("Failed to parse JSON file: " + e.getMessage())
                        .itemType("tool")
                        .build();
            } catch (Exception e) {
                log.error("Unexpected error during tool embedding generation", e);
                return EmbeddingResultExtended.builder()
                        .totalItems(toolNames.size())
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(toolNames.size())
                        .failedItems(toolNames)
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .message("Unexpected error: " + e.getMessage())
                        .itemType("tool")
                        .build();
            }
        });
    }

    private List<McpTool> filterToolsByNames(
            List<String> toolNames,
            Map<String, McpTool> allTools,
            boolean forceRegenerate) {

        List<McpTool> filtered = new ArrayList<>();
        for (String toolName : toolNames) {
            McpTool tool = allTools.get(toolName);
            if (tool != null) {
                if (!forceRegenerate && tool.getEmbedding() != null) {
                    log.debug("Tool {} already has embedding, skipping", toolName);
                    continue;
                }
                filtered.add(tool);
            } else {
                log.warn("Tool not found: {}", toolName);
            }
        }
        return filtered;
    }

    private int countSuccess(int[] result) {
        int count = 0;
        for (int num : result) {
            if (num > 0) {
                count++;
            }
        }
        return count;
    }

    public CompletableFuture<EmbeddingResultExtended> generateAllEmbeddings() {
        long embeddingStartTime = System.currentTimeMillis();
        log.info("Starting batch tool embedding generation for all tools");

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
                List<McpTool> allTools = McpJsonParser.flattenTools(skills);
                log.info("Found {} tools to process", allTools.size());

                CompletableFuture<List<McpTool>> embeddingFuture = embeddingResource.generateBatchEmbeddingsAsync(allTools);

                return embeddingFuture.thenCompose(processedTools -> {
                    long embeddingTimeMs = System.currentTimeMillis() - embeddingStartTime;
                    int embeddingSuccessCount = (int) processedTools.stream()
                            .filter(t -> t.getEmbedding() != null)
                            .count();
                    int embeddingFailureCount = processedTools.size() - embeddingSuccessCount;

                    CompletableFuture<int[]> dbFuture = CompletableFuture.supplyAsync(
                            () -> dbRepository.batchSaveToolEmbeddings(processedTools),
                            pgIoExecutor
                    );

                    CompletableFuture<Void> logFuture = logFileService.writeLogAsync(
                            EmbeddingResultExtended.builder()
                                    .totalItems(allTools.size())
                                    .embeddingSuccessCount(embeddingSuccessCount)
                                    .embeddingFailureCount(embeddingFailureCount)
                                    .failedItems(processedTools.stream()
                                            .filter(t -> t.getEmbedding() == null)
                                            .map(t -> t.getSkillName() + ":" + t.getToolName())
                                            .toList())
                                    .embeddingTimeMs(embeddingTimeMs)
                                    .databaseTimeMs(0)
                                    .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                                    .itemType("tool")
                                    .build(),
                            pgIoExecutor
                    );

                    return CompletableFuture.allOf(dbFuture, logFuture)
                            .thenCompose(v -> {
                                long dbTimeMs = System.currentTimeMillis() - embeddingStartTime - embeddingTimeMs;
                                int[] dbResult = dbFuture.join();
                                int dbSuccessCount = countSuccess(dbResult);
                                int dbFailureCount = dbResult.length - dbSuccessCount;

                                List<String> dbFailedItems = new ArrayList<>();
                                int successIndex = 0;
                                for (McpTool tool : processedTools) {
                                    if (tool.getEmbedding() != null) {
                                        if (dbResult[successIndex] > 0) {
                                            successIndex++;
                                        } else {
                                            dbFailedItems.add(tool.getSkillName() + ":" + tool.getToolName());
                                        }
                                    }
                                }

                                return CompletableFuture.completedFuture(EmbeddingResultExtended.builder()
                                        .totalItems(allTools.size())
                                        .embeddingSuccessCount(embeddingSuccessCount)
                                        .embeddingFailureCount(embeddingFailureCount)
                                        .failedItems(processedTools.stream()
                                                .filter(t -> t.getEmbedding() == null)
                                                .map(t -> t.getSkillName() + ":" + t.getToolName())
                                                .toList())
                                        .databaseSuccessCount(dbSuccessCount)
                                        .databaseFailureCount(dbFailureCount)
                                        .databaseFailedItems(dbFailedItems)
                                        .embeddingTimeMs(embeddingTimeMs)
                                        .databaseTimeMs(dbTimeMs)
                                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                                        .message(String.format("All tools embedding completed. Embedding: %d/%d, Database: %d/%d",
                                                embeddingSuccessCount, allTools.size(),
                                                dbSuccessCount, embeddingSuccessCount))
                                        .itemType("tool")
                                        .build());
                            });
                });

            } catch (IOException e) {
                log.error("Failed to parse JSON file", e);
                return EmbeddingResultExtended.builder()
                        .totalItems(0)
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(0)
                        .failedItems(new ArrayList<>())
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .message("Failed to parse JSON file: " + e.getMessage())
                        .itemType("tool")
                        .build();
            } catch (Exception e) {
                log.error("Unexpected error during all tools embedding generation", e);
                return EmbeddingResultExtended.builder()
                        .totalItems(0)
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(0)
                        .failedItems(new ArrayList<>())
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - embeddingStartTime)
                        .message("Unexpected error: " + e.getMessage())
                        .itemType("tool")
                        .build();
            }
        });
    }
}
