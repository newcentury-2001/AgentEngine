package com.agentengine.skill.preprocess.service;

import com.agentengine.skill.preprocess.config.SkillPromptTemplateConfig;
import com.agentengine.skill.preprocess.model.CleanedToolSemantic;
import com.agentengine.skill.preprocess.model.SkillLabelPrediction;
import com.agentengine.skill.preprocess.model.SkillPreprocessRequest;
import com.agentengine.skill.preprocess.model.SkillPreprocessResult;
import com.agentengine.skill.preprocess.model.ToolDescriptor;
import com.agentengine.skill.preprocess.model.ToolInstallView;
import com.agentengine.skill.preprocess.model.ToolVector;
import com.agentengine.skill.preprocess.util.ServerLabelExtractor;
import com.agentengine.skill.preprocess.util.VectorUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class SkillPreprocessService {

    private final ZhipuApiService zhipuApiService;
    private final ToolCallStatsService toolCallStatsService;
    private final SkillVectorStoreService skillVectorStoreService;
    private final SkillPromptTemplateConfig promptTemplateConfig;
    private final ExecutorService discoveryModelExecutor;
    private final ExecutorService semanticModelExecutor;
    private final ExecutorService labelModelExecutor;
    private final ExecutorService embeddingModelExecutor;

    public SkillPreprocessService(
            ZhipuApiService zhipuApiService,
            ToolCallStatsService toolCallStatsService,
            SkillVectorStoreService skillVectorStoreService,
            SkillPromptTemplateConfig promptTemplateConfig,
            @Qualifier("discoveryModelExecutor") ExecutorService discoveryModelExecutor,
            @Qualifier("semanticModelExecutor") ExecutorService semanticModelExecutor,
            @Qualifier("labelModelExecutor") ExecutorService labelModelExecutor,
            @Qualifier("embeddingModelExecutor") ExecutorService embeddingModelExecutor
    ) {
        this.zhipuApiService = zhipuApiService;
        this.toolCallStatsService = toolCallStatsService;
        this.skillVectorStoreService = skillVectorStoreService;
        this.promptTemplateConfig = promptTemplateConfig;
        this.discoveryModelExecutor = discoveryModelExecutor;
        this.semanticModelExecutor = semanticModelExecutor;
        this.labelModelExecutor = labelModelExecutor;
        this.embeddingModelExecutor = embeddingModelExecutor;
    }

    public SkillPreprocessResult preprocess(SkillPreprocessRequest request, boolean persist) {
        String input = safe(request.mcpServerUrl()).trim();
        if (input.isBlank()) {
            input = safe(request.curlExample()).trim();
        }
        if (input.isBlank()) {
            throw new IllegalArgumentException("mcpServerUrl is required");
        }
        final String finalInput = input;

        String resolvedSkillName = determineSkillName(request);
        List<ToolDescriptor> tools = CompletableFuture.supplyAsync(
                () -> zhipuApiService.fetchRawToolsFromMcp(finalInput),
                discoveryModelExecutor
        ).join();
        CompletableFuture<SkillLabelPrediction> skillLabelFuture = CompletableFuture.supplyAsync(() ->
                        zhipuApiService.classifySkillLabel(
                                buildIntentActionPromptFromSkillNameOnly(resolvedSkillName)
                ),
                labelModelExecutor
        );
        CompletableFuture<HashMap<String, Long>> recent7dCountsFuture = CompletableFuture.supplyAsync(
                toolCallStatsService::getRecent7dToolCounts,
                discoveryModelExecutor
        );
        List<CompletableFuture<ToolCleanResult>> toolCleanFutures = new ArrayList<>();
        for (ToolDescriptor tool : tools) {
            toolCleanFutures.add(CompletableFuture.supplyAsync(
                    () -> new ToolCleanResult(
                            tool,
                            zhipuApiService.cleanToolSemantic(buildToolCleaningPrompt(resolvedSkillName, tool))
                    ),
                    semanticModelExecutor
            ));
        }
        List<CompletableFuture<?>> layer1 = new ArrayList<>();
        layer1.add(skillLabelFuture);
        layer1.add(recent7dCountsFuture);
        layer1.addAll(toolCleanFutures);
        CompletableFuture.allOf(layer1.toArray(new CompletableFuture[0])).join();
        //第一层结束


        SkillLabelPrediction skillLabel = skillLabelFuture.join();
        String generatedSkillDescription = zhipuApiService.generateSkillDescription(
                buildSkillDescriptionPrompt(resolvedSkillName, skillLabel, tools)
        );
        String finalSkillDescription = generatedSkillDescription.isBlank()
                ? buildCompactSkillDescription(resolvedSkillName, skillLabel)
                : generatedSkillDescription;
        HashMap<String, Long> recent7dCounts = recent7dCountsFuture.join();

        List<ToolCleanResult> cleanedResults = new ArrayList<>();
        for (CompletableFuture<ToolCleanResult> f : toolCleanFutures) {
            cleanedResults.add(f.join());
        }

        CompletableFuture<double[]> skillEmbeddingFuture = CompletableFuture.supplyAsync(
                () -> zhipuApiService.embedding(finalSkillDescription),
                embeddingModelExecutor
        );
        List<CompletableFuture<ToolProcessResult>> toolVectorFutures = new ArrayList<>();
        for (ToolCleanResult cleanedResult : cleanedResults) {
            toolVectorFutures.add(CompletableFuture.supplyAsync(
                    () -> buildToolVectorWithHeat(cleanedResult.tool(), cleanedResult.cleaned(), recent7dCounts),
                    embeddingModelExecutor
            ));
        }
        List<CompletableFuture<?>> layer2 = new ArrayList<>();
        layer2.add(skillEmbeddingFuture);
        layer2.addAll(toolVectorFutures);
        CompletableFuture.allOf(layer2.toArray(new CompletableFuture[0])).join();

        List<ToolVector> toolVectors = new ArrayList<>();
        List<double[]> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (CompletableFuture<ToolProcessResult> f : toolVectorFutures) {
            ToolProcessResult r = f.join();
            toolVectors.add(r.toolVector());
            vectors.add(r.normalizedVector());
            weights.add(r.weight());
        }
        //第二层

        double[] toolPackageVector = VectorUtils.weightedAverage(vectors, weights);
        double[] normalizedToolPackage = VectorUtils.l2Normalize(toolPackageVector);

        double[] skillEmbedding = skillEmbeddingFuture.join();
        double[] normalizedSkill = VectorUtils.l2Normalize(skillEmbedding);

        double[] fused = VectorUtils.blend(normalizedSkill, 0.7, normalizedToolPackage, 0.3);
        double[] normalizedFinal = VectorUtils.l2Normalize(fused);

        if (persist) {
            skillVectorStoreService.save(
                    resolvedSkillName,
                    finalSkillDescription,
                    tools,
                    toolVectors,
                    normalizedSkill,
                    normalizedToolPackage,
                    normalizedFinal
            );
        }

        return new SkillPreprocessResult(
                resolvedSkillName,
                skillLabel,
                tools.stream().map(t -> new ToolInstallView(t.name(), t.description())).toList(),
                normalizedSkill,
                normalizedToolPackage,
                normalizedFinal,
                recent7dCounts,
                toolVectors
        );
    }

    private String buildIntentActionPromptFromSkillNameOnly(String skillName) {
        return """
                仅根据 skill_name 推断 intentTag 和 actionType。
                skill_name: %s

                规则：
                1) 只能输出 JSON。
                2) intentTag 仅可取：stat、rank、query、alert、execute、none。
                3) actionType 仅可取：read、write。
                4) 若无法判断，intentTag=none，actionType=read。

                输出格式：
                {"intentTag":"query","actionType":"read","confidence":0.0}
                """.formatted(safe(skillName));
    }

    private String buildSkillDescriptionPrompt(
            String skillName,
            SkillLabelPrediction skillLabel,
            List<ToolDescriptor> tools
    ) {
        String intent = skillLabel == null || skillLabel.intentTag() == null ? "none" : skillLabel.intentTag().code();
        String action = skillLabel == null || skillLabel.actionType() == null ? "read" : skillLabel.actionType().code();
        String toolNames = tools.stream().map(ToolDescriptor::name).reduce((a, b) -> a + ", " + b).orElse("");
        return """
                你是技能安装助手。请基于下列信息生成一句中文技能描述（20~40字）：
                - skill_name: %s
                - intent: %s
                - action: %s
                - tools: %s

                要求：
                1) 只输出一句描述，不要JSON，不要解释；
                2) 术语稳定，优先使用“查询/统计/排行/告警/执行”等词；
                3) 体现主要能力与动作类型（read/write）。
                """.formatted(skillName, intent, action, toolNames);
    }

    private String buildToolFallbackEmbeddingText(ToolDescriptor tool) {
        return "tool_name: " + tool.name() + "\n" +
                "description: " + tool.description() + "\n" +
                "input_schema: " + tool.inputSchema();
    }

    private String buildToolCleaningPrompt(String skillName, ToolDescriptor tool) {
        String template = promptTemplateConfig.getToolCleaningTemplate();
        return template
                .replace("{{skill_name}}", safe(skillName))
                .replace("{{server_label}}", safe(skillName))
                .replace("{{tool_raw_text}}", safe(tool.description()))
                .replace("{{tool_name_hint}}", safe(tool.name()))
                .replace("{{input_schema_raw}}", safe(tool.inputSchema()));
    }

    private String buildCompactSkillDescription(String skillName, SkillLabelPrediction skillLabel) {
        String intent = skillLabel == null || skillLabel.intentTag() == null ? "none" : skillLabel.intentTag().code();
        String action = skillLabel == null || skillLabel.actionType() == null ? "read" : skillLabel.actionType().code();
        return safe(skillName) + "|" + intent + "|" + action;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String determineSkillName(SkillPreprocessRequest request) {
        String fromRequest = safe(request.serverLabel()).trim();
        if (!fromRequest.isBlank()) {
            return fromRequest;
        }
        String url = safe(request.mcpServerUrl()).trim();
        if (!url.isBlank()) {
            String fromUrl = ServerLabelExtractor.fromServerUrl(url);
            if (!fromUrl.isBlank()) {
                return fromUrl;
            }
        }
        String fromCurl = zhipuApiService.parseServerLabelFromCurlOrUrl(safe(request.curlExample()));
        if (!fromCurl.isBlank()) {
            return fromCurl;
        }
        return "unknown-skill";
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private ToolProcessResult buildToolVectorWithHeat(
            ToolDescriptor tool,
            CleanedToolSemantic cleaned,
            HashMap<String, Long> recent7dCounts
    ) {
        String toolText = cleaned.embeddingText().isBlank() ? buildToolFallbackEmbeddingText(tool) : cleaned.embeddingText();
        double[] toolEmbedding = zhipuApiService.embedding(toolText);
        double[] normalizedTool = VectorUtils.l2Normalize(toolEmbedding);
        long w = Math.max(0L, recent7dCounts.getOrDefault(tool.name(), 0L));
        double heat = sigmoid(Math.log10(w + 1.0));
        ToolVector toolVector = new ToolVector(tool.name(), normalizedTool, w, heat);
        return new ToolProcessResult(toolVector, normalizedTool, heat);
    }

    private record ToolProcessResult(
            ToolVector toolVector,
            double[] normalizedVector,
            double weight
    ) {
    }

    private record ToolCleanResult(
            ToolDescriptor tool,
            CleanedToolSemantic cleaned
    ) {
    }
}
