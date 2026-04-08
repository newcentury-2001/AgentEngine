package com.agentengine.skill.embedding;

import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import com.agentengine.skill.embedding.service.EmbeddingService;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.config.LlmHttpClientPoolProperties;
import com.agentcommon.mcp.model.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmbeddingServiceTest {

    @Test
    void testDisabledEmbedding() throws Exception {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(false);

        var executor = Executors.newSingleThreadExecutor();
        try {
            EmbeddingService service = new EmbeddingService(
                    properties,
                    new LlmHttpClientRouter(new LlmHttpClientPoolProperties()),
                    new HttpRequestClient(),
                    new ObjectMapper(),
                    executor
            );

            McpTool tool = new McpTool();
            tool.setToolName("test_tool");
            tool.setToolDescription("Test tool description");

            List<McpTool> tools = service.generateEmbeddingsAsync(List.of(tool)).get();
            assertNotNull(tools);
            assertNull(tool.getEmbedding());
        } finally {
            executor.shutdownNow();
        }
    }
}
