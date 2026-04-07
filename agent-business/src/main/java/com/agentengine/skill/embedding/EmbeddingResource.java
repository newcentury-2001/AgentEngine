package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.parser.McpJsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Embedding 资源层
 * 负责调用第三方 embedding API 生成工具的向量
 */
@Slf4j
@Component
public class EmbeddingResource {

    private final EmbeddingProperties properties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ExecutorService embeddingExecutor;

    public EmbeddingResource(
            EmbeddingProperties properties,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.embeddingExecutor = embeddingExecutor;
    }

    /**
     * 异步生成单个工具的 embedding
     */
    public CompletableFuture<McpTool> generateEmbeddingAsync(McpTool tool) {
        if (!properties.isEnabled()) {
            log.debug("Embedding is disabled, skipping tool: {}", tool.getToolName());
            return CompletableFuture.completedFuture(tool);
        }

        log.debug("Generating embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建 prompt
                String prompt = buildPrompt(tool);

                // 调用 API（简单方式，直接设置编码）
                String requestBody = String.format(
                        "{\"model\":\"%s\",\"input\":\"%s\",\"encoding_format\":\"float\"}",
                        properties.getModel(),
                        URLEncoder.encode(prompt, StandardCharsets.UTF_8)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .build();

                HttpResponse<String> response = embeddingExecutor.submit(() -> httpClient.send(request));

                // 解析响应
                double[] embedding = parseEmbeddingResponse(response.body());

                // 设置 embedding
                tool.setEmbedding(embedding);
                log.debug("Successfully generated embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName());

                return tool;
            } catch (Exception e) {
                log.error("Failed to generate embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName(), e);
                tool.setEmbedding(null);
                return tool; // 返回工具，但 embedding 为 null
            }
        }, embeddingExecutor);
    }

    /**
     * 批量异步生成工具 embedding
     */
    public CompletableFuture<List<McpTool>> generateBatchEmbeddingsAsync(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return CompletableFuture.completedFuture(tools);
        }

        log.info("Starting batch embedding generation for {} tools", tools.size());

        // 为每个工具异步生成 embedding
        List<CompletableFuture<McpTool>> futures = tools.stream()
                .map(this::generateEmbeddingAsync)
                .toList();

        // 等待所有任务完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = (int) tools.stream()
                            .filter(t -> t.getEmbedding() != null)
                            .count();
                    int failureCount = tools.size() - successCount;
                    log.info("Batch embedding completed. Success: {}, Failure: {}",
                            successCount, failureCount);
                    return tools;
                });
    }

    /**
     * 构建 prompt
     */
    private String buildPrompt(McpTool tool) {
        return tool.getToolName() + ": " + tool.getToolDescription();
    }

    /**
     * 解析 embedding 响应
     */
    private double[] parseEmbeddingResponse(String response) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
        com.fasterxml.jackson.databind.JsonNode dataNode = root.path("data");
        if (dataNode.isArray() && dataNode.size() > 0) {
            com.fasterxml.jackson.databind.JsonNode embeddingNode = dataNode.get(0).path("embedding");
            if (embeddingNode.isArray()) {
                double[] embedding = new double[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = embeddingNode.get(i).asDouble();
                }
                return embedding;
            }
        }
        throw new RuntimeException("Invalid embedding response: " + response);
    }
}
