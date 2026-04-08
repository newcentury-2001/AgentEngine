package com.agentengine.skill.embedding.controller;

import com.agentengine.skill.embedding.model.pojo.SkillEmbeddingRequest;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import com.agentengine.skill.embedding.service.EmbeddingOrchestrationService;
import com.agentcommon.mcp.parser.McpJsonParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/skill-embedding")
@RequiredArgsConstructor
public class SkillEmbeddingController {

    private final EmbeddingOrchestrationService orchestrationService;

    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateEmbeddings(
            @RequestBody SkillEmbeddingRequest request) {

        log.info("Received skill embedding generation request for {} skills (includeTools: {})",
                request.getSkillNames().size(), request.isIncludeTools());

        return orchestrationService.generateSkillEmbeddingsByNames(
                request.getSkillNames(),
                request.isForceRegenerate(),
                request.isIncludeTools()
        ).thenApply(ResponseEntity::ok).exceptionally(ex -> {
            log.error("Skill embedding failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(request.getSkillNames().size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(request.getSkillNames().size())
                    .failedItems(request.getSkillNames())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Error: " + ex.getMessage())
                    .itemType("skill")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings(
            @RequestParam(defaultValue = "true") boolean includeTools,
            @RequestParam(defaultValue = "false") boolean forceRegenerate) {

        log.info("Received request to generate embeddings for all skills (includeTools: {}, forceRegenerate: {})",
                includeTools, forceRegenerate);

        return orchestrationService.generateAllSkillEmbeddings(includeTools, forceRegenerate)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("All skills embeddings generation failed", ex);
                    EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                            .totalItems(0)
                            .embeddingSuccessCount(0)
                            .embeddingFailureCount(0)
                            .failedItems(new ArrayList<>())
                            .databaseSuccessCount(0)
                            .databaseFailureCount(0)
                            .databaseFailedItems(new ArrayList<>())
                            .embeddingTimeMs(0)
                            .databaseTimeMs(0)
                            .totalTimeMs(0)
                            .message("Error: " + ex.getMessage())
                            .itemType("skill")
                            .build();
                    return ResponseEntity.internalServerError().body(errorResult);
                });
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<SkillEmbeddingStatusResponse>> getStatus(
            @RequestParam List<String> skillNames) {

        log.info("Received skill embedding status query for {} skills", skillNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                var skills = orchestrationService.loadSummarySkills();
                var skillMap = McpJsonParser.buildSkillNameMap(skills);

                List<String> existing = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                for (String skillName : skillNames) {
                    var skill = skillMap.get(skillName);
                    if (skill != null && skill.getEmbedding() != null) {
                        existing.add(skillName);
                    } else {
                        missing.add(skillName);
                    }
                }

                SkillEmbeddingStatusResponse response = SkillEmbeddingStatusResponse.builder()
                        .totalSkills(skillNames.size())
                        .existingCount(existing.size())
                        .missingCount(missing.size())
                        .existingSkills(existing)
                        .missingSkills(missing)
                        .build();

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Failed to query skill embedding status", e);
                return ResponseEntity.internalServerError().build();
            }
        });
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillEmbeddingStatusResponse {
        private int totalSkills;
        private int existingCount;
        private int missingCount;
        private List<String> existingSkills;
        private List<String> missingSkills;
    }
}
