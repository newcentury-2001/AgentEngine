package com.agentengine.skill.embedding.service;

import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Embedding 閺堝秴濮? */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingProperties properties;
    private final LlmHttpClientRouter llmHttpClientRouter;
    private final HttpRequestClient httpRequestClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService embeddingExecutor;

    /**
     * 閹靛綊鍣洪悽鐔稿灇瀹搞儱鍙块惃?embedding 閸氭垿鍣洪敍鍫濈磽濮濄儻绱?     */
    public CompletableFuture<List<McpTool>> generateEmbeddingsAsync(List<McpTool> tools) {
        if (!properties.isEnabled()) {
            log.info("Embedding generation is disabled, skipping");
            return CompletableFuture.completedFuture(tools);
        }

        log.info("Starting batch embedding generation for {} tools", tools.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failedTools = new ArrayList<>();

        List<CompletableFuture<Void>> futures = tools.stream()
                .map(tool -> CompletableFuture.supplyAsync(
                        () -> generateSingleEmbedding(tool),
                        embeddingExecutor
                ).thenAccept(embedding -> {
                    if (embedding != null) {
                        tool.setEmbedding(embedding);
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                        failedTools.add(tool.getSkillName() + ":" + tool.getToolName());
                    }
                }).exceptionally(ex -> {
                    log.error("Failed to generate embedding for tool: {}:{}",
                            tool.getSkillName(), tool.getToolName(), ex);
                    failureCount.incrementAndGet();
                    failedTools.add(tool.getSkillName() + ":" + tool.getToolName());
                    return null;
                }))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    // 閸愭瑥鍙嗛弮銉ョ箶閺傚洣娆?                    writeResultsToLog(successCount.get(), failureCount.get(), failedTools);

                    log.info("Batch embedding generation completed. Success: {}, Failure: {}",
                            successCount.get(), failureCount.get());

                    return tools;
                });
    }

    /**
     * 閻㈢喐鍨氶崡鏇氶嚋瀹搞儱鍙块惃?embedding 閸氭垿鍣?     */
    private double[] generateSingleEmbedding(McpTool tool) {
        try {
            String text = tool.getToolName() + ": " + tool.getToolDescription();

            // 鐠嬪啰鏁?embedding API
            String response = callEmbeddingApi(text);
            return parseEmbeddingResponse(response);
        } catch (Exception e) {
            log.error("Error generating embedding for tool: {}", tool.getToolName(), e);
            return null;
        }
    }

    /**
     * 鐠嬪啰鏁?embedding API
     */
    private String callEmbeddingApi(String text) throws Exception {
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        String requestBody = String.format(
                "{\"model\":\"%s\",\"input\":\"%s\",\"encoding_format\":\"float\"}",
                properties.getModel(), encodedText
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

    /**
     * 鐟欙絾鐎?embedding 閸濆秴绨?     */
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

    /**
     * 鐏忓棛绮ㄩ弸婊冨晸閸忋儲妫╄箛妤佹瀮娴?     */
    private void writeResultsToLog(int successCount, int failureCount, List<String> failedTools) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String logEntry = String.format(
                    """
                    ======================================
                    Embedding Generation Report
                    ======================================
                    Timestamp: %s
                    Total Success: %d
                    Total Failure: %d
                    ======================================
                    Failed Tools:
                    %s
                    ======================================
                    """,
                    timestamp,
                    successCount,
                    failureCount,
                    failedTools.isEmpty() ? "None" : failedTools.stream()
                            .sorted()
                            .collect(Collectors.joining("\n", "\n", "\n"))
            );

            Path logPath = Paths.get(properties.getLogFilePath());
            Files.createDirectories(logPath.getParent());

            Files.writeString(
                    logPath,
                    logEntry,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            log.info("Embedding results written to: {}", properties.getLogFilePath());
        } catch (Exception e) {
            log.error("Failed to write embedding results to log file", e);
        }
    }
}

