package com.agentengine.skill.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 技能 Embedding 生成控制器
 * 提供给前端的 REST API 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/skill-embedding")
@RequiredArgsConstructor
public class SkillEmbeddingController {

    private final SkillEmbeddingOrchestrationService orchestrationService;

    /**
     * 根据技能名称列表生成 embedding（包含入库和日志）
     *
     * @param request SkillEmbedding 请求
     * @return CompletableFuture 包含生成结果
     */
    @PostMapping("/generate")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateEmbeddings(
            @RequestBody SkillEmbeddingRequest request) {

        log.info("Received skill embedding generation request for {} skills (includeTools: {})",
                request.getSkillNames().size(), request.isIncludeTools());

        // 调用编排服务（包含入库和日志）
        return orchestrationService.generateEmbeddingsByNames(
                request.getSkillNames(),
                request.isForceRegenerate(),
                request.isIncludeTools()
        ).thenApply(result -> {
            log.info("Skill embedding completed: {}", result.getMessage());
            return ResponseEntity.ok(result);
        }).exceptionally(ex -> {
            log.error("Skill embedding failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                    .totalItems(request.getSkillNames().size())
                    .embeddingSuccessCount(0)
                    .embeddingFailureCount(request.getSkillNames().size())
                    .failedItems(request.getSkillNames())
                    .databaseSuccessCount(0)
                    .databaseFailureCount(0)
                    .databaseFailedItems(new java.util.ArrayList<>())
                    .embeddingTimeMs(0)
                    .databaseTimeMs(0)
                    .totalTimeMs(0)
                    .message("Error: " + ex.getMessage())
                    .itemType("skill")
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    /**
     * 批量生成所有技能的 embedding（包含入库和日志）
     *
     * @param includeTools 是否包含工具信息
     * @return CompletableFuture 包含生成结果
     */
    @PostMapping("/generate-all")
    public CompletableFuture<ResponseEntity<EmbeddingResultExtended>> generateAllEmbeddings(
            @RequestParam(defaultValue = "true") boolean includeTools) {

        log.info("Received request to generate embeddings for all skills (includeTools: {})",
                includeTools);

        return orchestrationService.generateAllEmbeddings(includeTools)
                .thenApply(result -> {
                    log.info("All skills embeddings generation completed: {}", result.getMessage());
                    return ResponseEntity.ok(result);
                }).exceptionally(ex -> {
            log.error("All skills embeddings generation failed", ex);
            EmbeddingResultExtended errorResult = EmbeddingResultExtended.builder()
                            .totalItems(0)
                            .embeddingSuccessCount(0)
                            .embeddingFailureCount(0)
                            .failedItems(new java.util.ArrayList<>())
                            .databaseSuccessCount(0)
                            .databaseFailureCount(0)
                            .databaseFailedItems(new java.util.ArrayList<>())
                            .embeddingTimeMs(0)
                            .databaseTimeMs(0)
                            .totalTimeMs(0)
                            .message("Error: " + ex.getMessage())
                            .itemType("skill")
                            .build();
            return ResponseEntity.internalServerError().body(errorResult);
        });
    }

    /**
     * 查询指定技能的 embedding 状态
     *
     * @param skillNames 技能名称列表
     * @return CompletableFuture 包含状态信息
     */
    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<SkillEmbeddingStatusResponse>> getStatus(
            @RequestParam List<String> skillNames) {

        log.info("Received skill embedding status query for {} skills", skillNames.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 解析 JSON 文件
                var skills = com.agentengine.skill.parser.McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
                var skillMap = skills.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.agentengine.skill.model.McpSkill::getSkillName,
                                s -> s,
                                (s1, s2) -> s1
                        ));

                List<String> existing = new java.util.ArrayList<>();
                List<String> missing = new java.util.ArrayList<>();

                for (String skillName : skillNames) {
                    var skill = skillMap.get(skillName);
                    if (skill != null && skill.getEmbedding() != null) {
                        existing.add(skillName);
                    } else if (skill == null) {
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

    /**
     * 技能 Embedding 状态响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SkillEmbeddingStatusResponse {
        private int totalSkills;
        private int existingCount;
        private int missingCount;
        private List<String> existingSkills;
        private List<String> missingSkills;
    }
}
