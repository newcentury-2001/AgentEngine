package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.parser.McpJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 工具 Embedding 生成器
 * 整合 JSON 解析和 embedding 生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolEmbeddingGenerator {

    private final EmbeddingService embeddingService;

    /**
     * 从 JSON 文件解析并生成 embedding（异步）
     *
     * @param jsonFilePath JSON 文件路径
     * @return 包含 embedding 的技能列表
     */
    public CompletableFuture<List<McpSkill>> parseAndGenerateEmbeddings(String jsonFilePath) {
        try {
            // 解析 JSON 文件
            List<McpSkill> skills = McpJsonParser.parseFromFile(jsonFilePath);
            log.info("Parsed {} skills from file: {}", skills.size(), jsonFilePath);

            // 扁平化所有工具
            List<McpTool> allTools = McpJsonParser.flattenTools(skills);
            log.info("Total tools to generate embeddings: {}", allTools.size());

            // 生成 embedding
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

    /**
     * 直接为已解析的工具列表生成 embedding（异步）
     *
     * @param skills 技能列表
     * @return 包含 embedding 的技能列表
     */
    public CompletableFuture<List<McpSkill>> generateEmbeddingsForSkills(List<McpSkill> skills) {
        // 扁平化所有工具
        List<McpTool> allTools = McpJsonParser.flattenTools(skills);
        log.info("Starting embedding generation for {} tools", allTools.size());

        // 生成 embedding
        return embeddingService.generateEmbeddingsAsync(allTools)
                .thenApply(tools -> {
                    log.info("Embedding generation completed");
                    return skills;
                });
    }

    /**
     * 直接为工具列表生成 embedding（异步）
     *
     * @param tools 工具列表
     * @return 包含 embedding 的工具列表
     */
    public CompletableFuture<List<McpTool>> generateEmbeddingsForTools(List<McpTool> tools) {
        log.info("Starting embedding generation for {} tools", tools.size());
        return embeddingService.generateEmbeddingsAsync(tools);
    }
}
