package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 技能 Embedding 资源层
 * 负责调用第三方 embedding API 生成技能的向量
 */
@Slf4j
@Component
public class SkillEmbeddingResource {

    private final EmbeddingProperties properties;
    private final HttpClient httpClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ExecutorService embeddingExecutor;

    public SkillEmbeddingResource(
            EmbeddingProperties properties,
            @Qualifier("embeddingHttpClient") HttpClient httpClient,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.embeddingExecutor = embeddingExecutor;
    }

    /**
     * 异步生成单个技能的 embedding
     *
     * @param skill 技能对象
     * @param includeTools 是否包含工具信息
     * @return CompletableFuture 包含 embedding 的技能对象
     */
    public CompletableFuture<McpSkill> generateEmbeddingAsync(McpSkill skill, boolean includeTools) {
        if (!properties.isEnabled()) {
            log.debug("Embedding is disabled, skipping skill: {}", skill.getSkillName());
            return CompletableFuture.completedFuture(skill);
        }

        log.debug("Generating embedding for skill: {} (includeTools: {})",
                skill.getSkillName(), includeTools);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建 prompt
                String prompt = buildSkillPrompt(skill, includeTools);

                // 调用 API
                String response = callEmbeddingApi(prompt);

                // 解析响应
                double[] embedding = parseEmbeddingResponse(response);

                // 设置 embedding
                skill.setEmbedding(embedding);
                log.debug("Successfully generated embedding for skill: {}", skill.getSkillName());

                return skill;
            } catch (Exception e) {
                log.error("Failed to generate embedding for skill: {}", skill.getSkillName(), e);
                skill.setEmbedding(null);
                return skill; // 返回技能，但 embedding 为 null
            }
        }, embeddingExecutor);
    }

    /**
     * 批量异步生成技能 embedding
     *
     * @param skills 技能列表
     * @param includeTools 是否包含工具信息
     * @return CompletableFuture 包含所有技能的列表
     */
    public CompletableFuture<List<McpSkill>> generateBatchEmbeddingsAsync(
            List<McpSkill> skills,
            boolean includeTools) {
        if (skills == null || skills.isEmpty()) {
            return CompletableFuture.completedFuture(skills);
        }

        log.info("Starting batch skill embedding generation for {} skills (includeTools: {})",
                skills.size(), includeTools);

        // 为每个技能异步生成 embedding
        List<CompletableFuture<McpSkill>> futures = skills.stream()
                .map(skill -> generateEmbeddingAsync(skill, includeTools))
                .toList();

        // 等待所有任务完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = (int) skills.stream()
                            .filter(s -> s.getEmbedding() != null)
                            .count();
                    int failureCount = skills.size() - successCount;
                    log.info("Batch skill embedding completed. Success: {}, Failure: {}",
                            successCount, failureCount);
                    return skills;
                });
    }

    /**
     * 构建技能 embedding prompt
     */
    private String buildSkillPrompt(McpSkill skill, boolean includeTools) {
        StringBuilder prompt = new StringBuilder();

        // 添加技能基本信息
        prompt.append("Skill: ").append(skill.getSkillName()).append("\n");
        prompt.append("Description: ").append(skill.getSkillDescription()).append("\n");

        // 添加意图
        if (skill.getIntent() != null && !skill.getIntent().isBlank()) {
            prompt.append("Intent: ").append(skill.getIntent()).append("\n");
        }

        // 添加动作类型
        if (skill.getActionType() != null && !skill.getActionType().isBlank()) {
            prompt.append("Action Type: ").append(skill.getActionType()).append("\n");
        }

        // 添加标签
        if (skill.getTags() != null && !skill.getTags().isEmpty()) {
            prompt.append("Tags: ").append(String.join(", ", skill.getTags())).append("\n");
        }

        // 添加工具信息
        if (includeTools && skill.getTools() != null && !skill.getTools().isEmpty()) {
            prompt.append("\nTools:\n");
            for (var tool : skill.getTools()) {
                prompt.append("  - ").append(tool.getToolName())
                        .append(": ").append(tool.getToolDescription())
                        .append("\n");
            }
        }

        return prompt.toString();
    }

    /**
     * 调用 embedding API
     */
    private String callEmbeddingApi(String prompt) throws Exception {
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);

        String requestBody = String.format(
                "{\"model\":\"%s\",\"input\":\"%s\",\"encoding_format\":\"float\"}",
                properties.getModel(), encodedPrompt
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Embedding API returned status: " + response.statusCode() +
                    ", body: " + response.body()
            );
        }

        return response.body();
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
