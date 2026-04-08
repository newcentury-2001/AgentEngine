package com.agentengine.skill.embedding;

import com.agentengine.skill.embedding.controller.EmbeddingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmbeddingApiIntegrationTest {

    @Autowired(required = false)
    private WebTestClient webTestClient;

    @Test
    void testStatusQuery() {
        if (webTestClient == null) {
            return;
        }

        EmbeddingController.EmbeddingStatusResponse response = webTestClient.get()
                .uri("/api/embedding/status?toolNames=recognition:location_recognition")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody(EmbeddingController.EmbeddingStatusResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
    }
}
