package com.agentengine.skill.embedding.resource;

import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.McpSkill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class SkillEmbeddingResource {

    private final EmbeddingProperties properties;
    private final LlmHttpClientRouter llmHttpClientRouter;
    private final HttpRequestClient httpRequestClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService embeddingExecutor;

    public SkillEmbeddingResource(
            EmbeddingProperties properties,
            LlmHttpClientRouter llmHttpClientRouter,
            HttpRequestClient httpRequestClient,
            ObjectMapper objectMapper,
            @Qualifier("embeddingExecutor") ExecutorService embeddingExecutor) {
        this.properties = properties;
        this.llmHttpClientRouter = llmHttpClientRouter;
        this.httpRequestClient = httpRequestClient;
        this.objectMapper = objectMapper;
        this.embeddingExecutor = embeddingExecutor;
    }

    public CompletableFuture<McpSkill> generateEmbeddingAsync(McpSkill skill, boolean includeTools) {
        if (!properties.isEnabled()) {
            log.debug("Embedding is disabled, skipping skill: {}", skill.getSkillName());
            return CompletableFuture.completedFuture(skill);
        }

        log.debug("Generating embedding for skill: {} (includeTools: {})", skill.getSkillName(), includeTools);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildSkillPrompt(skill, includeTools);
                String response = callEmbeddingApi(prompt);
                double[] embedding = parseEmbeddingResponse(response);

                skill.setEmbedding(embedding);
                log.debug("Successfully generated embedding for skill: {}", skill.getSkillName());
                return skill;
            } catch (Exception e) {
                if (includeTools && isBadRequestParameter(e)) {
                    try {
                        log.warn("Embedding bad request for skill [{}] with includeTools=true, retry with includeTools=false",
                                skill.getSkillName());
                        String fallbackPrompt = buildSkillPrompt(skill, false);
                        String fallbackResp = callEmbeddingApi(fallbackPrompt);
                        double[] fallbackEmbedding = parseEmbeddingResponse(fallbackResp);
                        skill.setEmbedding(fallbackEmbedding);
                        log.debug("Fallback embedding succeeded for skill: {}", skill.getSkillName());
                        return skill;
                    } catch (Exception retryEx) {
                        log.error("Fallback embedding also failed for skill: {}", skill.getSkillName(), retryEx);
                    }
                }
                log.error("Failed to generate embedding for skill: {}", skill.getSkillName(), e);
                skill.setEmbedding(null);
                return skill;
            }
        }, embeddingExecutor);
    }

    /**
     * 批量技能 embedding 处理方法，当前由 MQ 消费链路触发。
     */
    public CompletableFuture<List<McpSkill>> generateBatchEmbeddingsAsync(
            List<McpSkill> skills, boolean includeTools) {
        if (skills == null || skills.isEmpty()) {
            return CompletableFuture.completedFuture(skills);
        }

        log.info("Starting batch skill embedding generation for {} skills (includeTools: {})",
                skills.size(), includeTools);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (McpSkill skill : skills) {
            // 将每个异步任务串成顺序链：前一个完成后再执行下一个（非并行）。
            chain = chain.thenCompose(v -> generateEmbeddingAsync(skill, includeTools).thenApply(one -> null));
        }

        return chain.thenApply(v -> {
            int successCount = (int) skills.stream().filter(s -> s.getEmbedding() != null).count();
            int failureCount = skills.size() - successCount;
            log.info("Batch skill embedding completed. Success: {}, Failure: {}", successCount, failureCount);
            return skills;
        });
    }

    private String buildSkillPrompt(McpSkill skill, boolean includeTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Skill: ").append(skill.getSkillName()).append("\n");
        prompt.append("Description: ").append(skill.getSkillDescription()).append("\n");

        if (skill.getIntent() != null && !skill.getIntent().isBlank()) {
            prompt.append("Intent: ").append(skill.getIntent()).append("\n");
        }
        if (skill.getActionType() != null && !skill.getActionType().isBlank()) {
            prompt.append("Action Type: ").append(skill.getActionType()).append("\n");
        }
        if (skill.getTags() != null && !skill.getTags().isEmpty()) {
            prompt.append("Tags: ").append(String.join(", ", skill.getTags())).append("\n");
        }

        if (includeTools && skill.getTools() != null && !skill.getTools().isEmpty()) {
            prompt.append("\nTools:\n");
            skill.getTools().forEach(tool -> prompt.append("  - ")
                    .append(tool.getToolName())
                    .append(": ")
                    .append(tool.getToolDescription())
                    .append("\n"));
        }

        return prompt.toString();
    }

    private String callEmbeddingApi(String prompt) throws Exception {
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        String requestBody = String.format(
                "{\"model\":\"%s\",\"input\":\"%s\",\"encoding_format\":\"float\"}",
                properties.getModel(), encodedPrompt
        );

        HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                        properties.getBaseUrl(),
                        ZhipuHttpProtocol.EMBEDDINGS_PATH,
                        properties.getApiKey()
                )
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpRequestClient.send(llmHttpClientRouter.getClient(properties.getModel()), request);
    }

    private double[] parseEmbeddingResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode dataNode = root.path("data");
        if (dataNode.isArray() && dataNode.size() > 0) {
            JsonNode embeddingNode = dataNode.get(0).path("embedding");
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

    private boolean isBadRequestParameter(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("status: 400") || msg.contains("\"code\":\"1210\"");
    }
}
