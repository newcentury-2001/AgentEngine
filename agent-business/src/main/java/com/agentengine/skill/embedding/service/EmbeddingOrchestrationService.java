package com.agentengine.skill.embedding.service;

import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.repository.EmbeddingDbRepository;
import com.agentengine.skill.embedding.resource.EmbeddingResource;
import com.agentengine.skill.embedding.resource.SkillEmbeddingResource;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingOrchestrationService {

    private final EmbeddingResource embeddingResource;
    private final SkillEmbeddingResource skillEmbeddingResource;
    private final EmbeddingDbRepository dbRepository;
    private final EmbeddingLogFileService logFileService;
    private final ResourceLoader resourceLoader;

    @Qualifier("pgIoExecutor")
    private final ExecutorService pgIoExecutor;

    @Value("${slot.summary.path:dataset/mcp_final_summary.json}")
    private String summaryPath;

    public CompletableFuture<EmbeddingResultExtended> generateToolEmbeddingsByNames(
            List<String> toolNames,
            boolean forceRegenerate) {

        long startTime = System.currentTimeMillis();
        log.info("Starting tool embedding generation for {} tools", toolNames.size());

        try {
            List<McpTool> targetTools = loadAndFilterTools(toolNames, forceRegenerate);
            if (targetTools.isEmpty()) {
                return createEmptyResult(toolNames.size(), startTime, "tool");
            }
            return processToolEmbeddingWorkflow(targetTools, startTime, "tool");
        } catch (Exception e) {
            log.error("Failed to generate tool embeddings", e);
            return createErrorResult(toolNames.size(), startTime, "Failed to generate tool embeddings: " + e.getMessage(), "tool");
        }
    }

    public CompletableFuture<EmbeddingResultExtended> generateAllToolEmbeddings(boolean forceRegenerate) {
        long startTime = System.currentTimeMillis();
        log.info("Starting embedding generation for all tools");

        try {
            List<McpTool> allTools = loadAllTools(forceRegenerate);
            if (allTools.isEmpty()) {
                return createEmptyResult(0, startTime, "tool");
            }
            return processToolEmbeddingWorkflow(allTools, startTime, "tool");
        } catch (Exception e) {
            log.error("Failed to generate embeddings for all tools", e);
            return createErrorResult(0, startTime, "Failed to generate all tool embeddings: " + e.getMessage(), "tool");
        }
    }

    public CompletableFuture<EmbeddingResultExtended> generateSkillEmbeddingsByNames(
            List<String> skillNames,
            boolean forceRegenerate,
            boolean includeTools) {

        long startTime = System.currentTimeMillis();
        log.info("Starting skill embedding generation for {} skills (includeTools: {})", skillNames.size(), includeTools);

        try {
            List<McpSkill> targetSkills = loadAndFilterSkills(skillNames, forceRegenerate);
            if (targetSkills.isEmpty()) {
                return createEmptyResult(skillNames.size(), startTime, "skill");
            }
            return processSkillEmbeddingWorkflow(targetSkills, includeTools, startTime, "skill");
        } catch (Exception e) {
            log.error("Failed to generate skill embeddings", e);
            return createErrorResult(skillNames.size(), startTime, "Failed to generate skill embeddings: " + e.getMessage(), "skill");
        }
    }

    public CompletableFuture<EmbeddingResultExtended> generateAllSkillEmbeddings(boolean includeTools, boolean forceRegenerate) {
        long startTime = System.currentTimeMillis();
        log.info("Starting embedding generation for all skills (includeTools: {})", includeTools);

        try {
            List<McpSkill> allSkills = loadAllSkills(forceRegenerate);
            if (allSkills.isEmpty()) {
                return createEmptyResult(0, startTime, "skill");
            }
            return processSkillEmbeddingWorkflow(allSkills, includeTools, startTime, "skill");
        } catch (Exception e) {
            log.error("Failed to generate embeddings for all skills", e);
            return createErrorResult(0, startTime, "Failed to generate all skill embeddings: " + e.getMessage(), "skill");
        }
    }

    public List<McpSkill> loadSummarySkills() throws IOException {
        return readSummarySkills();
    }

    private List<McpTool> loadAndFilterTools(List<String> toolNames, boolean forceRegenerate) throws IOException {
        List<McpSkill> skills = readSummarySkills();
        Map<String, McpTool> allTools = McpJsonParser.buildToolKeyMap(skills);
        return filterToolsByNames(toolNames, allTools, forceRegenerate);
    }

    private List<McpTool> loadAllTools(boolean forceRegenerate) throws IOException {
        List<McpTool> allTools = McpJsonParser.flattenTools(readSummarySkills());
        if (forceRegenerate) {
            return allTools;
        }
        return allTools.stream().filter(t -> t.getEmbedding() == null).toList();
    }

    private List<McpSkill> loadAndFilterSkills(List<String> skillNames, boolean forceRegenerate) throws IOException {
        List<McpSkill> skills = readSummarySkills();
        Map<String, McpSkill> allSkills = McpJsonParser.buildSkillNameMap(skills);
        return filterSkillsByNames(skillNames, allSkills, forceRegenerate);
    }

    private List<McpSkill> loadAllSkills(boolean forceRegenerate) throws IOException {
        List<McpSkill> allSkills = readSummarySkills();
        if (forceRegenerate) {
            return allSkills;
        }
        return allSkills.stream().filter(s -> s.getEmbedding() == null).toList();
    }

    private CompletableFuture<EmbeddingResultExtended> processToolEmbeddingWorkflow(
            List<McpTool> targetTools,
            long startTime,
            String itemType) {

        long embeddingStartTime = System.currentTimeMillis();
        return embeddingResource.generateBatchEmbeddingsAsync(targetTools)
                .thenCompose(processedTools -> {
                    long embeddingTimeMs = System.currentTimeMillis() - embeddingStartTime;
                    return handleToolEmbeddingResults(processedTools, targetTools.size(), embeddingTimeMs, startTime, itemType);
                });
    }

    private CompletableFuture<EmbeddingResultExtended> processSkillEmbeddingWorkflow(
            List<McpSkill> targetSkills,
            boolean includeTools,
            long startTime,
            String itemType) {

        long embeddingStartTime = System.currentTimeMillis();
        return skillEmbeddingResource.generateBatchEmbeddingsAsync(targetSkills, includeTools)
                .thenCompose(processedSkills -> {
                    long embeddingTimeMs = System.currentTimeMillis() - embeddingStartTime;
                    return handleSkillEmbeddingResults(processedSkills, targetSkills.size(), embeddingTimeMs, startTime, itemType);
                });
    }

    private CompletableFuture<EmbeddingResultExtended> handleToolEmbeddingResults(
            List<McpTool> processedTools,
            int totalItems,
            long embeddingTimeMs,
            long startTime,
            String itemType) {

        int embeddingSuccessCount = (int) processedTools.stream().filter(t -> t.getEmbedding() != null).count();
        int embeddingFailureCount = processedTools.size() - embeddingSuccessCount;

        List<String> failedItems = processedTools.stream()
                .filter(t -> t.getEmbedding() == null)
                .map(t -> t.getSkillName() + ":" + t.getToolName())
                .collect(Collectors.toList());

        CompletableFuture<int[]> dbFuture = CompletableFuture.supplyAsync(
                () -> dbRepository.batchSaveToolEmbeddings(processedTools),
                pgIoExecutor
        );

        CompletableFuture<Void> logFuture = logFileService.writeLogAsync(
                createIntermediateResult(totalItems, embeddingSuccessCount, embeddingFailureCount, failedItems, embeddingTimeMs, startTime, itemType),
                pgIoExecutor
        );

        return CompletableFuture.allOf(dbFuture, logFuture)
                .thenApply(v -> {
                    long dbTimeMs = System.currentTimeMillis() - startTime - embeddingTimeMs;
                    int[] dbResult = dbFuture.join();
                    return createFinalResult(
                            totalItems,
                            embeddingSuccessCount,
                            embeddingFailureCount,
                            failedItems,
                            dbResult,
                            extractDbFailedToolItems(processedTools, dbResult),
                            embeddingTimeMs,
                            dbTimeMs,
                            startTime,
                            itemType
                    );
                });
    }

    private CompletableFuture<EmbeddingResultExtended> handleSkillEmbeddingResults(
            List<McpSkill> processedSkills,
            int totalItems,
            long embeddingTimeMs,
            long startTime,
            String itemType) {

        int embeddingSuccessCount = (int) processedSkills.stream().filter(s -> s.getEmbedding() != null).count();
        int embeddingFailureCount = processedSkills.size() - embeddingSuccessCount;

        List<String> failedItems = processedSkills.stream()
                .filter(s -> s.getEmbedding() == null)
                .map(McpSkill::getSkillName)
                .collect(Collectors.toList());

        CompletableFuture<int[]> dbFuture = CompletableFuture.supplyAsync(
                () -> dbRepository.batchSaveSkillEmbeddings(processedSkills),
                pgIoExecutor
        );

        CompletableFuture<Void> logFuture = logFileService.writeLogAsync(
                createIntermediateResult(totalItems, embeddingSuccessCount, embeddingFailureCount, failedItems, embeddingTimeMs, startTime, itemType),
                pgIoExecutor
        );

        return CompletableFuture.allOf(dbFuture, logFuture)
                .thenApply(v -> {
                    long dbTimeMs = System.currentTimeMillis() - startTime - embeddingTimeMs;
                    int[] dbResult = dbFuture.join();
                    return createFinalResult(
                            totalItems,
                            embeddingSuccessCount,
                            embeddingFailureCount,
                            failedItems,
                            dbResult,
                            extractDbFailedSkillItems(processedSkills, dbResult),
                            embeddingTimeMs,
                            dbTimeMs,
                            startTime,
                            itemType
                    );
                });
    }

    private EmbeddingResultExtended createIntermediateResult(
            int totalItems,
            int embeddingSuccessCount,
            int embeddingFailureCount,
            List<String> failedItems,
            long embeddingTimeMs,
            long startTime,
            String itemType) {

        return EmbeddingResultExtended.builder()
                .totalItems(totalItems)
                .embeddingSuccessCount(embeddingSuccessCount)
                .embeddingFailureCount(embeddingFailureCount)
                .failedItems(failedItems)
                .embeddingTimeMs(embeddingTimeMs)
                .databaseTimeMs(0)
                .totalTimeMs(System.currentTimeMillis() - startTime)
                .itemType(itemType)
                .build();
    }

    private EmbeddingResultExtended createFinalResult(
            int totalItems,
            int embeddingSuccessCount,
            int embeddingFailureCount,
            List<String> failedItems,
            int[] dbResult,
            List<String> dbFailedItems,
            long embeddingTimeMs,
            long dbTimeMs,
            long startTime,
            String itemType) {

        int dbSuccessCount = countSuccess(dbResult);
        int dbFailureCount = dbResult.length - dbSuccessCount;

        return EmbeddingResultExtended.builder()
                .totalItems(totalItems)
                .embeddingSuccessCount(embeddingSuccessCount)
                .embeddingFailureCount(embeddingFailureCount)
                .failedItems(failedItems)
                .databaseSuccessCount(dbSuccessCount)
                .databaseFailureCount(dbFailureCount)
                .databaseFailedItems(dbFailedItems)
                .embeddingTimeMs(embeddingTimeMs)
                .databaseTimeMs(dbTimeMs)
                .totalTimeMs(System.currentTimeMillis() - startTime)
                .message(String.format("Embedding completed. Embedding: %d/%d, Database: %d/%d",
                        embeddingSuccessCount, totalItems, dbSuccessCount, embeddingSuccessCount))
                .itemType(itemType)
                .build();
    }

    private List<String> extractDbFailedToolItems(List<McpTool> processedTools, int[] dbResult) {
        List<String> dbFailedItems = new ArrayList<>();
        int successIndex = 0;
        for (McpTool tool : processedTools) {
            if (tool.getEmbedding() != null) {
                if (successIndex < dbResult.length && dbResult[successIndex] <= 0) {
                    dbFailedItems.add(tool.getSkillName() + ":" + tool.getToolName());
                }
                successIndex++;
            }
        }
        return dbFailedItems;
    }

    private List<String> extractDbFailedSkillItems(List<McpSkill> processedSkills, int[] dbResult) {
        List<String> dbFailedItems = new ArrayList<>();
        int successIndex = 0;
        for (McpSkill skill : processedSkills) {
            if (skill.getEmbedding() != null) {
                if (successIndex < dbResult.length && dbResult[successIndex] <= 0) {
                    dbFailedItems.add(skill.getSkillName());
                }
                successIndex++;
            }
        }
        return dbFailedItems;
    }

    private CompletableFuture<EmbeddingResultExtended> createEmptyResult(int totalCount, long startTime, String itemType) {
        return CompletableFuture.completedFuture(
                EmbeddingResultExtended.builder()
                        .totalItems(totalCount)
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(totalCount)
                        .failedItems(new ArrayList<>())
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - startTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - startTime)
                        .message("No " + itemType + " found for embedding generation")
                        .itemType(itemType)
                        .build()
        );
    }

    private CompletableFuture<EmbeddingResultExtended> createErrorResult(
            int totalCount,
            long startTime,
            String errorMessage,
            String itemType) {

        return CompletableFuture.completedFuture(
                EmbeddingResultExtended.builder()
                        .totalItems(totalCount)
                        .embeddingSuccessCount(0)
                        .embeddingFailureCount(totalCount)
                        .failedItems(new ArrayList<>())
                        .databaseSuccessCount(0)
                        .databaseFailureCount(0)
                        .databaseFailedItems(new ArrayList<>())
                        .embeddingTimeMs(System.currentTimeMillis() - startTime)
                        .databaseTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - startTime)
                        .message(errorMessage)
                        .itemType(itemType)
                        .build()
        );
    }

    private List<McpTool> filterToolsByNames(
            List<String> toolNames,
            Map<String, McpTool> allTools,
            boolean forceRegenerate) {

        List<McpTool> filtered = new ArrayList<>();
        for (String toolName : toolNames) {
            McpTool tool = allTools.get(toolName);
            if (tool == null) {
                log.warn("Tool not found: {}", toolName);
                continue;
            }
            if (!forceRegenerate && tool.getEmbedding() != null) {
                log.debug("Tool {} already has embedding, skipping", toolName);
                continue;
            }
            filtered.add(tool);
        }
        return filtered;
    }

    private List<McpSkill> filterSkillsByNames(
            List<String> skillNames,
            Map<String, McpSkill> allSkills,
            boolean forceRegenerate) {

        List<McpSkill> filtered = new ArrayList<>();
        for (String skillName : skillNames) {
            McpSkill skill = allSkills.get(skillName);
            if (skill == null) {
                log.warn("Skill not found: {}", skillName);
                continue;
            }
            if (!forceRegenerate && skill.getEmbedding() != null) {
                log.debug("Skill {} already has embedding, skipping", skillName);
                continue;
            }
            filtered.add(skill);
        }
        return filtered;
    }

    private List<McpSkill> readSummarySkills() throws IOException {
        if (summaryPath == null || summaryPath.isBlank()) {
            throw new IOException("slot.summary.path is blank");
        }

        if (summaryPath.startsWith("classpath:") || summaryPath.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(summaryPath);
            if (!resource.exists()) {
                throw new IOException("Summary resource not found: " + summaryPath);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return McpJsonParser.parseFromStream(inputStream);
            }
        }

        File summaryFile = new File(summaryPath);
        if (summaryFile.exists()) {
            return McpJsonParser.parseFromFile(summaryPath);
        }

        Resource classpathResource = resourceLoader.getResource("classpath:" + summaryPath);
        if (classpathResource.exists()) {
            try (InputStream inputStream = classpathResource.getInputStream()) {
                return McpJsonParser.parseFromStream(inputStream);
            }
        }

        throw new IOException("Summary file not found for path: " + summaryPath);
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
}
