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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Service
public class SkillPreprocessService {

    private final ZhipuApiService zhipuApiService;
    private final ToolCallStatsService toolCallStatsService;
    private final SkillVectorStoreService skillVectorStoreService;
    private final SkillPromptTemplateConfig promptTemplateConfig;
    private final ExecutorService ioModelExecutor;
    private final ExecutorService cpuComputeExecutor;

    public SkillPreprocessService(
            ZhipuApiService zhipuApiService,
            ToolCallStatsService toolCallStatsService,
            SkillVectorStoreService skillVectorStoreService,
            SkillPromptTemplateConfig promptTemplateConfig,
            @Qualifier("ioModelExecutor") ExecutorService ioModelExecutor,
            @Qualifier("cpuComputeExecutor") ExecutorService cpuComputeExecutor
    ) {
        this.zhipuApiService = zhipuApiService;
        this.toolCallStatsService = toolCallStatsService;
        this.skillVectorStoreService = skillVectorStoreService;
        this.promptTemplateConfig = promptTemplateConfig;
        this.ioModelExecutor = ioModelExecutor;
        this.cpuComputeExecutor = cpuComputeExecutor;
    }

    public SkillPreprocessResult preprocess(SkillPreprocessRequest request, boolean persist) {
        final String finalInput = resolveMcpInputOrThrow(request);

        String resolvedSkillName = determineSkillName(request);
        if (persist && !skillVectorStoreService.canInstallSkill(resolvedSkillName)) {
            throw new IllegalStateException("skill already exists or is installing: " + resolvedSkillName);
        }
        List<ToolDescriptor> tools = supplyAsyncWithRequestContext(
                () -> zhipuApiService.fetchRawToolsFromMcp(finalInput),
                ioModelExecutor
        ).join();
        CompletableFuture<SkillLabelPrediction> skillLabelFuture = supplyAsyncWithRequestContext(() ->
                        zhipuApiService.classifySkillLabel(
                                buildIntentActionPromptFromSkillNameOnly(resolvedSkillName)
                ),
                ioModelExecutor
        );
        CompletableFuture<HashMap<String, Long>> recent7dCountsFuture = supplyAsyncWithRequestContext(
                toolCallStatsService::getRecent7dToolCounts,
                ioModelExecutor
        );
        List<CompletableFuture<ToolCleanResult>> toolCleanFutures = new ArrayList<>();
        for (ToolDescriptor tool : tools) {
            toolCleanFutures.add(supplyAsyncWithRequestContext(
                    () -> new ToolCleanResult(
                            tool,
                            zhipuApiService.cleanToolSemantic(buildToolCleaningPrompt(resolvedSkillName, tool))
                    ),
                    ioModelExecutor
            ));
        }
        // 第一层：并行完成意图/动作识别、近7日计数读取、每个工具语义清洗。
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

        CompletableFuture<double[]> skillEmbeddingFuture = supplyAsyncWithRequestContext(
                () -> zhipuApiService.embedding(finalSkillDescription),
                ioModelExecutor
        );
        List<CompletableFuture<ToolProcessResult>> toolVectorFutures = new ArrayList<>();
        for (ToolCleanResult cleanedResult : cleanedResults) {
            toolVectorFutures.add(supplyAsyncWithRequestContext(
                    () -> buildToolVectorWithHeat(cleanedResult.tool(), cleanedResult.cleaned(), recent7dCounts),
                    ioModelExecutor
            ));
        }
        // 第二层：并行完成技能描述向量化，以及每个工具向量化与热度权重计算。
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

        double[] skillEmbedding = skillEmbeddingFuture.join();
        double[] normalizedSkill = CompletableFuture.supplyAsync(
                () -> VectorUtils.l2Normalize(skillEmbedding),
                cpuComputeExecutor
        ).join();

        double[] normalizedToolPackage = CompletableFuture.supplyAsync(() -> {
                    double[] toolPackageVector = VectorUtils.weightedAverage(vectors, weights);
                    return VectorUtils.l2Normalize(toolPackageVector);
                },
                cpuComputeExecutor
        ).join();

        double[] normalizedFinal = CompletableFuture.supplyAsync(() -> {
                    double[] fused = VectorUtils.blend(normalizedSkill, 0.7, normalizedToolPackage, 0.3);
                    return VectorUtils.l2Normalize(fused);
                },
                cpuComputeExecutor
        ).join();

        // 第三层：将技能向量与工具包向量按权重融合，并归一化得到最终技能向量。
        if (persist) {
            // 第四层：可选持久化，写入工具语义表、工具向量表、技能向量快照表。
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

        // 第五层：组装返回结果（对外返回精简字段，内部保留计算结果用于后续扩展）。
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

    /**
     * 从请求中解析并校验 MCP JSON 输入。
     * <p>
     * 要求 {@code mcpServerUrl} 为非空 JSON，且至少包含一个合法的
     * {@code mcpServers.*.url}。校验通过后返回原始 JSON 字符串。
     *
     * @throws IllegalArgumentException MCP JSON 缺失或格式不合法
     */
    private String resolveMcpInputOrThrow(SkillPreprocessRequest request) {
        String mcpJson = safe(request.mcpServerUrl()).trim();
        if (mcpJson.isBlank()) {
            throw new IllegalArgumentException("mcp json is required");
        }
        zhipuApiService.parseServerUrlFromMcpJsonOrThrow(mcpJson);
        return mcpJson;
    }

    /**
     * 根据 MCP JSON 中的 URL 解析技能名。
     * <p>
     * 先从 {@code mcpServerUrl} 提取服务 URL，再按既定规则（如 proxy/{skill}/mcp）
     * 解析为技能名；解析失败则抛出异常，避免写入无效技能标识。
     *
     * @throws IllegalArgumentException MCP JSON 不合法或无法解析技能名
     */
    private String determineSkillName(SkillPreprocessRequest request) {
        String mcpInput = safe(request.mcpServerUrl()).trim();
        String extractedUrl = zhipuApiService.parseServerUrlFromMcpJsonOrThrow(mcpInput);
        String skillName = ServerLabelExtractor.fromServerUrl(extractedUrl);
        if (skillName.isBlank()) {
            throw new IllegalArgumentException("mcp json invalid: cannot resolve skill name");
        }
        return skillName;
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

    /**
     * 在线程池中异步执行任务，并透传当前请求上下文。
     * 用于让异步线程可读取 RequestContextHolder 中的请求属性，执行后再恢复/清理上下文，避免污染线程复用现场。
     */
    private <T> CompletableFuture<T> supplyAsyncWithRequestContext(Supplier<T> supplier, ExecutorService executor) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return CompletableFuture.supplyAsync(() -> {
            RequestAttributes previous = RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes);
            }
            try {
                return supplier.get();
            } finally {
                if (previous != null) {
                    RequestContextHolder.setRequestAttributes(previous);
                } else {
                    RequestContextHolder.resetRequestAttributes();
                }
            }
        }, executor);
    }
}
