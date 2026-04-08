package com.agentengine.skill.embedding.resource;

import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.McpTool;
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
public class EmbeddingResource {

    private final EmbeddingProperties properties;
    private final LlmHttpClientRouter llmHttpClientRouter;
    private final HttpRequestClient httpRequestClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService embeddingExecutor;

    public EmbeddingResource(
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

    public CompletableFuture<McpTool> generateEmbeddingAsync(McpTool tool) {
        if (!properties.isEnabled()) {
            log.debug("Embedding is disabled, skipping tool: {}", tool.getToolName());
            return CompletableFuture.completedFuture(tool);
        }

        log.debug("Generating embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName());

        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildPrompt(tool);
                String requestBody = String.format(
                        "{\"model\":\"%s\",\"input\":\"%s\",\"encoding_format\":\"float\"}",
                        properties.getModel(),
                        URLEncoder.encode(prompt, StandardCharsets.UTF_8)
                );

                HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                                properties.getBaseUrl(),
                                ZhipuHttpProtocol.EMBEDDINGS_PATH,
                                properties.getApiKey()
                        )
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                String response = httpRequestClient.send(
                        llmHttpClientRouter.getClient(properties.getModel()), request);
                double[] embedding = parseEmbeddingResponse(response);

                tool.setEmbedding(embedding);
                log.debug("Successfully generated embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName());
                return tool;
            } catch (Exception e) {
                log.error("Failed to generate embedding for tool: {}:{}", tool.getSkillName(), tool.getToolName(), e);
                tool.setEmbedding(null);
                return tool;
            }
        }, embeddingExecutor);
    }

    public CompletableFuture<List<McpTool>> generateBatchEmbeddingsAsync(List<McpTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return CompletableFuture.completedFuture(tools);
        }

        log.info("Starting batch embedding generation for {} tools", tools.size());

        List<CompletableFuture<McpTool>> futures = tools.stream()
                .map(this::generateEmbeddingAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = (int) tools.stream().filter(t -> t.getEmbedding() != null).count();
                    int failureCount = tools.size() - successCount;
                    log.info("Batch embedding completed. Success: {}, Failure: {}", successCount, failureCount);
                    return tools;
                });
    }

    private String buildPrompt(McpTool tool) {
        return tool.getToolName() + ": " + tool.getToolDescription();
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
}
