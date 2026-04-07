package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding 服务测试
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddingServiceTest {

    private ToolEmbeddingGenerator generator;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // 注意：这里需要实际配置 API Key 才能运行测试
        // 如果没有 API Key，测试会被跳过
        String apiKey = System.getenv("zhipukey");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Warning: zhipukey environment variable not set, skipping embedding tests");
            return;
        }

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setApiKey(apiKey);
        properties.setEnabled(true);

        embeddingService = new EmbeddingService(
                properties,
                new java.net.http.HttpClient() {
                    @Override
                    public java.net.http.HttpClient.Version version() {
                        return Version.HTTP_2;
                    }

                    @Override
                    public Optional<java.net.http.HttpClient.Redirect> followRedirects() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.net.http.HttpClient.ProxySelector> proxy() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.net.http.HttpClient.Authenticator> authenticator() {
                        return Optional.empty();
                    }

                    @Override
                    public java.net.http.HttpClient.Version version() {
                        return Version.HTTP_2;
                    }

                    @Override
                    public <T> java.net.http.HttpResponse<T> send(
                            java.net.http.HttpRequest request,
                            java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler)
                            throws IOException, InterruptedException {
                        // Mock implementation for testing
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public <T> CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                            java.net.http.HttpRequest request,
                            java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
                        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented in test"));
                    }

                    @Override
                    public Optional<java.net.http.HttpClient.SSLContext> sslContext() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.net.http.HttpClient.SSLParameters> sslParameters() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Executor> executor() {
                        return Optional.empty();
                    }

                    @Override
                    public java.net.http.HttpClient.Builder connectTimeout(Duration duration) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder followRedirects(Redirect policy) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder proxy(ProxySelector proxySelector) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder authenticator(Authenticator authenticator) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder version(Version version) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder executor(Executor executor) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder sslContext(SSLContext sslContext) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }

                    @Override
                    public java.net.http.HttpClient.Builder sslParameters(SSLParameters sslParameters) {
                        throw new UnsupportedOperationException("Not implemented in test");
                    }
                },
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null // executor
        );

        generator = new ToolEmbeddingGenerator(embeddingService);
    }

    @Test
    void testParseAndGenerateEmbeddings() throws Exception {
        // 此测试需要实际的 API Key
        String apiKey = System.getenv("zhipukey");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Skipping test - no API key provided");
            return;
        }

        // 测试解析和生成 embedding
        List<McpSkill> skills = generator.parseAndGenerateEmbeddings("dataset/mcp_final_summary.json").get();

        assertNotNull(skills, "Skills should not be null");
        assertFalse(skills.isEmpty(), "Skills should not be empty");

        // 检查是否有工具生成了 embedding
        int embeddingCount = 0;
        for (McpSkill skill : skills) {
            if (skill.getTools() != null) {
                for (McpTool tool : skill.getTools()) {
                    if (tool.getEmbedding() != null) {
                        embeddingCount++;
                        assertEquals(1024, tool.getEmbedding().length, "Embedding should be 1024 dimensions");
                    }
                }
            }
        }

        assertTrue(embeddingCount > 0, "At least one tool should have embedding");
        System.out.println("Generated embeddings for " + embeddingCount + " tools");
    }

    @Test
    void testDisabledEmbedding() throws Exception {
        // 创建禁用的配置
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(false);

        EmbeddingService service = new EmbeddingService(
                properties,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null
        );

        // 创建简单的测试工具
        McpTool tool = new McpTool();
        tool.setToolName("test_tool");
        tool.setToolDescription("Test tool description");

        // 测试禁用时不生成 embedding
        List<McpTool> tools = service.generateEmbeddingsAsync(List.of(tool)).get();

        assertNotNull(tools);
        assertNull(tool.getEmbedding(), "Embedding should be null when disabled");
    }
}
