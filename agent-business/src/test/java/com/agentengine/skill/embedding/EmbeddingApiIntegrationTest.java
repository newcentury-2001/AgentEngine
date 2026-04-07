package com.agentengine.skill.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding API 集成测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmbeddingApiIntegrationTest {

    @Autowired(required = false)
    private WebTestClient webTestClient;

    /**
     * 测试状态查询接口
     */
    @Test
    void testStatusQuery() {
        if (webTestClient == null) {
            System.out.println("WebTestClient not available, skipping test");
            return;
        }

        EmbeddingController.EmbeddingStatusResponse response = webTestClient.get()
                .uri("/api/embedding/status?toolNames=recognition:location_recognition&toolNames=recognition:person_recognition")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(EmbeddingController.EmbeddingStatusResponse.class)
                .block();

        assertNotNull(response);
        assertEquals(2, response.getTotalTools());
        assertNotNull(response.getExistingTools());
        assertNotNull(response.getMissingTools());

        System.out.println("Status Query Result:");
        System.out.println("  Total: " + response.getTotalTools());
        System.out.println("  Existing: " + response.getExistingCount());
        System.out.println("  Missing: " + response.getMissingCount());
    }

    /**
     * 测试生成指定工具的 embedding
     */
    @Test
    void testGenerateEmbeddings() {
        if (webTestClient == null) {
            System.out.println("WebTestClient not available, skipping test");
            return;
        }

        EmbeddingRequest request = new EmbeddingRequest(
                List.of("recognition:location_recognition"),
                false
        );

        EmbeddingResult response = webTestClient.post()
                .uri("/api/embedding/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResult.class)
                .block();

        assertNotNull(response);
        assertTrue(response.getTotalTools() > 0);
        assertNotNull(response.getMessage());

        System.out.println("Generate Embeddings Result:");
        System.out.println("  Total: " + response.getTotalTools());
        System.out.println("  Success: " + response.getSuccessCount());
        System.out.println("  Failure: " + response.getFailureCount());
        System.out.println("  Time: " + response.getProcessingTimeMs() + "ms");
        System.out.println("  Message: " + response.getMessage());
    }

    /**
     * 测试生成所有工具的 embedding
     */
    @Test
    void testGenerateAllEmbeddings() {
        if (webTestClient == null) {
            System.out.println("WebTestClient");
            return;
        }

        EmbeddingResult response = webTestClient.post()
                .uri("/api/embedding/generate-all")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(EmbeddingResult.class)
                .block();

        assertNotNull(response);
        assertNotNull(response.getMessage());

        System.out.println("Generate All Embeddings Result:");
        System.out.println("  Success: " + response.getSuccessCount());
        System.out.println("  Failure: " + response.getFailureCount());
        System.out.println("  Time: " + response.getProcessingTimeMs() + "ms");
        System.out.println("  Message: " + response.getMessage());
    }
}
