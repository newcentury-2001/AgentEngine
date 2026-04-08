package com.agentengine.skill.embedding.service;

import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolEmbeddingGenerator {

    private final EmbeddingService embeddingService;

    public CompletableFuture<List<McpSkill>> parseAndGenerateEmbeddings(String jsonFilePath) {
        try {
            List<McpSkill> skills = McpJsonParser.parseFromFile(jsonFilePath);
            log.info("Parsed {} skills from file: {}", skills.size(), jsonFilePath);

            List<McpTool> allTools = McpJsonParser.flattenTools(skills);
            log.info("Total tools to generate embeddings: {}", allTools.size());

            return embeddingService.generateEmbeddingsAsync(allTools)
                    .thenApply(tools -> {
                        log.info("Embedding generation completed for {} tools", tools.size());
                        return skills;
                    });
        } catch (IOException e) {
            log.error("Failed to parse JSON file: {}", jsonFilePath, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<McpSkill>> generateEmbeddingsForSkills(List<McpSkill> skills) {
        List<McpTool> allTools = McpJsonParser.flattenTools(skills);
        log.info("Starting embedding generation for {} tools", allTools.size());

        return embeddingService.generateEmbeddingsAsync(allTools)
                .thenApply(tools -> {
                    log.info("Embedding generation completed");
                    return skills;
                });
    }

    public CompletableFuture<List<McpTool>> generateEmbeddingsForTools(List<McpTool> tools) {
        log.info("Starting embedding generation for {} tools", tools.size());
        return embeddingService.generateEmbeddingsAsync(tools);
    }
}
