package com.agentengine.web.assistant.service;

import com.agentcommon.concurrent.ExecutorSaturatedException;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;
import com.agentengine.web.assistant.mq.AssistantToolRetryPublisher;
import com.agentengine.web.assistant.mq.AssistantToolRetryContext;
import com.agentengine.web.assistant.mq.AssistantToolRetryTaskMessage;
import com.agentengine.web.assistant.model.AssistantPlannedTool;
import com.agentengine.web.assistant.model.AssistantInferenceResult;
import com.agentengine.web.assistant.service.retrieval.SkillVectorRecord;
import com.agentengine.web.assistant.service.stage.AssistantStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantInferenceService {
    private static final String INTENT_ENTITY_SYSTEM_PROMPT_TEMPLATE = """
            You are an entity extractor. Output JSON only.
            JSON schema:
            {
              "entities": [
                {"name":"slotKey","value":"slotValue"}
              ]
            }
            Rules:
            1) Extract structured entities only.
            2) name must be chosen from whitelist: %s
            3) Ignore values not in whitelist.
            4) Return empty array when no entities.
            """;

    private static final String INTENT_VOTE_SYSTEM_PROMPT = """
            You are an intent vote model. Output JSON only.
            JSON schema:
            {
              "needTool": boolean,
              "answerReady": boolean,
              "toolName": string
            }
            Rules:
            1) Decide whether external tool call is required.
            2) needTool=true when tool call is required.
            3) answerReady=true when direct answer is possible.
            4) Return empty toolName when unknown.
            """;

    private static final String CLARIFICATION_SLOT_SYSTEM_PROMPT = """
            You are a slot filling extractor. Output JSON only.
            JSON schema:
            {
              "toolName": string,
              "missingSlots": string[],
              "answerReady": boolean,
              "entities": [{"name":"slotKey","value":"slotValue"}]
            }
            Rules:
            1) Fill slots using latest user input and expectedMissingSlots.
            2) entities.name must be chosen strictly from expectedMissingSlots.
            3) Do not output aliases outside expectedMissingSlots.
            4) If missing remains: answerReady=false and return missingSlots; else answerReady=true and missingSlots=[].
            """;

    private static final String ACTIVE_INTENT_DRIFT_SYSTEM_PROMPT = """
            You are an intent-drift checker for ACTIVE state. Output JSON only.
            JSON schema:
            {
              "intentDrift": true/false,
              "reason": "short text"
            }
            Rules:
            1) Return true only when user clearly starts a new goal unrelated to current intent/skill.
            2) For ambiguous utterances or slot-filling utterances, return false.
            3) If user input is just parameter补充/确认/追问当前任务细节, return false.
            4) Be conservative: when uncertain, return false.
            """;


    private final LlmHttpClientRouter llmHttpClientRouter;
    private final HttpRequestClient httpRequestClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final AssistantToolRetryPublisher assistantToolRetryPublisher;
    private final ExecutorService assistantToolHttpExecutor;

    @Value("${zhipu.api-key:}")
    private String apiKey;

    @Value("${zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${zhipu.slot-model:glm-4-flash}")
    private String slotModel;

    @Value("${zhipu.embedding-model:embedding-3}")
    private String embeddingModel;

    @Value("${agent.assistant.slot-summary.path:dataset/mcp_final_summary.json}")
    private String slotSummaryPath;

    @Value("${agent.assistant.intent-slot-whitelist.path:dataset/assistant_intent_slot_whitelist.json}")
    private String intentSlotWhitelistPath;

    @Value("${agent.assistant.prompt.tool-output-analyze.path:classpath:prompt/assistant_tool_output_analyze_system_prompt.txt}")
    private String toolOutputAnalyzePromptPath;

    @Value("${agent.assistant.tool-http.timeout-ms:30000}")
    private long toolHttpTimeoutMs;

    @Value("${agent.assistant.tool-http.retry.max-retry:3}")
    private int maxToolRetryCount;

    private volatile Set<String> globalSlotWhitelistCache;
    private volatile String toolOutputAnalyzeSystemPromptCache;

    public AssistantInferenceResult inferForIntent(List<AssistantDialogueService.DialogueMessage> recentContext,
                                                   String userMessage) {
        AssistantInferenceResult entities = doIntentEntityExtraction(userMessage);
        AssistantInferenceResult vote = doIntentVote(recentContext, userMessage);
        return AssistantInferenceResult.builder()
                .needTool(vote.isNeedTool())
                .answerReady(vote.isAnswerReady())
                .toolName(vote.getToolName())
                .missingSlots(List.of())
                .errorMessage(vote.getErrorMessage())
                .entityMemory(entities.getEntityMemory())
                .build();
    }

    public AssistantInferenceResult inferForSlotFill(AssistantStage stage,
                                                     List<String> expectedMissingSlots,
                                                     String userMessage) {
        return doClarificationSlotExtraction(stage, expectedMissingSlots, userMessage);
    }

    public IntentDriftDecision detectIntentDriftInActive(String currentIntent,
                                                         String currentSkill,
                                                         List<String> missingSlots,
                                                         List<AssistantPlannedTool> pendingTools,
                                                         String context,
                                                         String userMessage) {
        try {
            List<String> pendingToolNames = pendingTools == null ? List.of() : pendingTools.stream()
                    .map(AssistantPlannedTool::getToolName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList();
            String missingJson = objectMapper.writeValueAsString(missingSlots == null ? List.of() : missingSlots);
            String pendingJson = objectMapper.writeValueAsString(pendingToolNames);
            String user = new StringBuffer(512)
                    .append("currentIntent=").append(text(currentIntent))
                    .append("\ncurrentSkill=").append(text(currentSkill))
                    .append("\nmissingSlots=").append(missingJson)
                    .append("\npendingTools=").append(pendingJson)
                    .append("\nrecentContext=").append(text(context))
                    .append("\nuserInput=").append(text(userMessage))
                    .toString();
            JsonNode parsed = tryParseJson(chatJson(ACTIVE_INTENT_DRIFT_SYSTEM_PROMPT, user));
            boolean drift = parsed.path("intentDrift").asBoolean(false);
            String reason = text(parsed.path("reason").asText(""));
            return new IntentDriftDecision(drift, reason);
        } catch (Exception e) {
            log.warn("active intent drift detection failed, fallback no-drift", e);
            return new IntentDriftDecision(false, "");
        }
    }

    private AssistantInferenceResult doIntentEntityExtraction(String userMessage) {
        // Extract entities from current user input, constrained by whitelist.
        try {
            Set<String> slotWhitelist = getGlobalSlotWhitelist();
            String whitelistJson = objectMapper.writeValueAsString(slotWhitelist);
            String systemPrompt = INTENT_ENTITY_SYSTEM_PROMPT_TEMPLATE.formatted(whitelistJson);

            String requestBody = objectMapper.createObjectNode()
                    .put("model", slotModel)
                    .put("temperature", 0)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt))
                            .add(objectMapper.createObjectNode().put("role", "user").put("content", userMessage)))
                    .toString();
            HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                            baseUrl, ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH, apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            String response = httpRequestClient.send(llmHttpClientRouter.getClient(slotModel), request);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode parsed = tryParseJson(content);
            return AssistantInferenceResult.builder()
                    .needTool(false)
                    .answerReady(false)
                    .toolName("")
                    .missingSlots(List.of())
                    .entityMemory(parseEntities(parsed.path("entities"), slotWhitelist))
                    .build();
        } catch (Exception e) {
            log.warn("intent entity extraction failed, fallback to empty entities", e);
            return AssistantInferenceResult.builder()
                    .needTool(false)
                    .answerReady(false)
                    .toolName("")
                    .missingSlots(List.of())
                    .entityMemory(Map.of())
                    .build();
        }
    }

    private AssistantInferenceResult doIntentVote(List<AssistantDialogueService.DialogueMessage> recentContext,
                                                  String userMessage) {
        try {
            String voteInput = buildContext(recentContext, userMessage);
            String requestBody = objectMapper.createObjectNode()
                    .put("model", slotModel)
                    .put("temperature", 0)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode().put("role", "system").put("content", INTENT_VOTE_SYSTEM_PROMPT))
                            .add(objectMapper.createObjectNode().put("role", "user").put("content", voteInput)))
                    .toString();
            HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                            baseUrl, ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH, apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            String response = httpRequestClient.send(llmHttpClientRouter.getClient(slotModel), request);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode parsed = tryParseJson(content);
            boolean needTool = parsed.path("needTool").asBoolean(false);
            boolean answerReady = parsed.path("answerReady").asBoolean(!needTool);
            String toolName = parsed.path("toolName").asText("");
            return AssistantInferenceResult.builder()
                    .needTool(needTool)
                    .answerReady(answerReady)
                    .toolName(toolName)
                    .missingSlots(List.of())
                    .build();
        } catch (Exception e) {
            log.warn("intent vote failed, fallback to direct answer", e);
            return AssistantInferenceResult.builder()
                    .needTool(false)
                    .answerReady(true)
                    .toolName("")
                    .missingSlots(List.of())
                    .build();
        }
    }

    private AssistantInferenceResult doClarificationSlotExtraction(AssistantStage stage,
                                                                   List<String> expectedMissingSlots,
                                                                   String userMessage) {
        try {
            String expected = expectedMissingSlots == null ? "[]" : objectMapper.writeValueAsString(expectedMissingSlots);
            String userContent = "currentStage=" + stage + "\nexpectedMissingSlots=" + expected + "\nuserInput=" + userMessage;
            String requestBody = objectMapper.createObjectNode()
                    .put("model", slotModel)
                    .put("temperature", 0)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode().put("role", "system").put("content", CLARIFICATION_SLOT_SYSTEM_PROMPT))
                            .add(objectMapper.createObjectNode().put("role", "user").put("content", userContent)))
                    .toString();

            HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                            baseUrl, ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH, apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            String response = httpRequestClient.send(llmHttpClientRouter.getClient(slotModel), request);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            JsonNode parsed = tryParseJson(content);
            String toolName = parsed.path("toolName").asText("");
            List<String> missingSlots = new ArrayList<>();
            JsonNode slotsNode = parsed.path("missingSlots");
            if (slotsNode.isArray()) {
                for (JsonNode node : slotsNode) {
                    String slot = node.asText("").trim();
                    if (!slot.isEmpty()) {
                        missingSlots.add(slot);
                    }
                }
            }
            boolean answerReady = parsed.path("answerReady").asBoolean(missingSlots.isEmpty());
            return AssistantInferenceResult.builder()
                    .needTool(true)
                    .answerReady(answerReady)
                    .toolName(toolName)
                    .missingSlots(missingSlots)
                    .entityMemory(parseEntities(parsed.path("entities"), Set.of()))
                    .build();
        } catch (Exception e) {
            log.warn("clarification slot extraction failed, fallback to conservative result", e);
            return AssistantInferenceResult.builder()
                    .needTool(true)
                    .answerReady(false)
                    .toolName("")
                    .missingSlots(expectedMissingSlots == null ? List.of("slot_fill_failed") : expectedMissingSlots)
                    .entityMemory(Map.of())
                    .build();
        }
    }

    public double[] embedQuery(String text) {
        try {
            String requestBody = objectMapper.createObjectNode()
                    .put("model", embeddingModel)
                    .put("input", text == null ? "" : text)
                    .put("encoding_format", "float")
                    .toString();
            HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                            baseUrl, ZhipuHttpProtocol.EMBEDDINGS_PATH, apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            String response = httpRequestClient.send(llmHttpClientRouter.getClient(embeddingModel), request);
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                return new double[0];
            }
            double[] result = new double[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                result[i] = embeddingNode.get(i).asDouble(0D);
            }
            return result;
        } catch (Exception e) {
            log.warn("embed query failed", e);
            return new double[0];
        }
    }

    public String rerankBestSkill(String context, String intent, List<SkillVectorRecord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        String fallback = candidates.get(0).getSkillName();
        try {
            String candidateJson = objectMapper.writeValueAsString(candidates.stream()
                    .map(s -> Map.of(
                            "skillName", s.getSkillName(),
                            "skillDescription", s.getSkillDescription(),
                            "intent", s.getIntent()))
                    .toList());
            String system = """
                    You are a skill reranker. Output JSON only.
                    JSON schema: {"bestSkill":"", "reason":""}
                    Rules: bestSkill must come from candidates.
                    """;
            String user = "intent=" + intent + "\ncontext=" + context + "\ncandidates=" + candidateJson;
            JsonNode parsed = tryParseJson(chatJson(system, user));
            String best = parsed.path("bestSkill").asText("").trim();
            if (best.isEmpty()) {
                return fallback;
            }
            boolean exists = candidates.stream().anyMatch(s -> best.equals(s.getSkillName()));
            return exists ? best : fallback;
        } catch (Exception e) {
            log.warn("rerank skill failed, fallback to top similarity", e);
            return fallback;
        }
    }

    public List<String> selectTools(String context, String intent, String skillName, List<AssistantPlannedTool> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> fallback = List.of(candidates.get(0).getToolName());
        try {
            String candidateJson = objectMapper.writeValueAsString(candidates.stream()
                    .map(t -> Map.of(
                            "toolName", t.getToolName(),
                            "toolDescription", t.getToolDescription(),
                            "simScore", t.getSimScore() == null ? 0D : t.getSimScore(),
                            "heatWeight", t.getHeatWeight() == null ? 0D : t.getHeatWeight(),
                            "hardRequiredSlots", resolveHardRequiredSlots(t),
                            "requiredSlots", resolveHardRequiredSlots(t),
                            "optionalSlots", resolveOptionalSlots(t)))
                    .toList());
            String system = """
                    You are a tool selector. Output JSON only.
                    JSON schema: {"selectedTools":["toolA","toolB"],"executionOrder":["toolA"],"reason":""}
                    Rules: select from candidates only, max 3 tools.
                    """;
            String user = "intent=" + intent + "\nskill=" + skillName + "\ncontext=" + context + "\ncandidates=" + candidateJson;
            JsonNode parsed = tryParseJson(chatJson(system, user));
            List<String> selected = new ArrayList<>();
            JsonNode arr = parsed.path("selectedTools");
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String name = item.asText("").trim();
                    if (!name.isEmpty()) {
                        selected.add(name);
                    }
                }
            }
            if (selected.isEmpty()) {
                return fallback;
            }
            Set<String> candidateNames = candidates.stream().map(AssistantPlannedTool::getToolName).collect(Collectors.toSet());
            List<String> filtered = selected.stream().filter(candidateNames::contains).distinct().limit(3).toList();
            return filtered.isEmpty() ? fallback : filtered;
        } catch (Exception e) {
            log.warn("select tools failed, fallback to top similarity", e);
            return fallback;
        }
    }
    /**
     * Execute one planned tool via direct MCP tools/call (JSON-RPC over HTTP).
     * Retryable failures are sent to RocketMQ delayed queue, non-retryable failures fast fail.
     */
    public ToolExecutionResult executePlannedTool(String taskId,
                                                  String intent,
                                                  String skillName,
                                                  AssistantPlannedTool tool,
                                                  Map<String, String> entities) {
        try {
            if (tool == null || blank(tool.getServerUrl()) || blank(tool.getToolName())) {
                return ToolExecutionResult.fastFailed("Tool config invalid: missing serverUrl/toolName");
            }
            if (blank(apiKey)) {
                return ToolExecutionResult.fastFailed("Tool execution unavailable now (missing API key).");
            }
            String argsJson = objectMapper.writeValueAsString(buildToolArgs(tool, entities));
            String content = assistantToolHttpExecutor
                    .submit(() -> invokeRealMcpTool(intent, skillName, tool, argsJson))
                    .get(Math.max(1000L, toolHttpTimeoutMs), TimeUnit.MILLISECONDS);
            if (blank(content)) {
                return ToolExecutionResult.fastFailed("Tool returned empty response.");
            }
            return ToolExecutionResult.succeeded(content.trim());
        } catch (Exception e) {
            Throwable root = unwrapExecutionException(e);
            String toolName = tool == null ? "" : tool.getToolName();
            if (isRetryable(root)) {
                int baseRetryCount = Math.max(0, AssistantToolRetryContext.retryCount());
                int maxRetry = AssistantToolRetryContext.maxRetry() > 0
                        ? AssistantToolRetryContext.maxRetry()
                        : Math.max(1, maxToolRetryCount);
                if (baseRetryCount >= maxRetry) {
                    return ToolExecutionResult.fastFailed("Tool temporarily unavailable and retry limit reached.");
                }
                int nextRetryCount = baseRetryCount + 1;
                AssistantToolRetryTaskMessage msg = new AssistantToolRetryTaskMessage();
                msg.setTaskId(text(taskId));
                msg.setIntent(text(intent));
                msg.setSkillName(text(skillName));
                msg.setToolName(toolName);
                msg.setServerUrl(tool == null ? "" : text(tool.getServerUrl()));
                msg.setArgsJson(safeBuildArgsJson(tool, entities));
                msg.setRetryCount(nextRetryCount);
                msg.setMaxRetry(maxRetry);
                msg.setCreatedAtEpochMs(System.currentTimeMillis());
                boolean queued = assistantToolRetryPublisher.sendWithDelayRetry(msg, nextRetryCount);
                if (queued) {
                    log.warn("tool execution retry queued. taskId={}, skill={}, tool={}, retry={}/{}, reason={}",
                            taskId, skillName, toolName, nextRetryCount, maxRetry, safeMessage(root));
                    return ToolExecutionResult.retryScheduled("Tool is busy now, queued for delayed retry.");
                }
                log.error("tool retry queue publish failed. taskId={}, skill={}, tool={}", taskId, skillName, toolName, root);
                return ToolExecutionResult.fastFailed("Tool is temporarily busy, retry queue unavailable. Please retry later.");
            }
            log.warn("tool execution fast failed. taskId={}, skill={}, tool={}", taskId, skillName, toolName, root);
            return ToolExecutionResult.fastFailed("Tool call failed: " + safeMessage(root));
        }
    }

    public record ToolExecutionResult(
            ToolExecutionStatus status,
            String rawOutput,
            String message
    ) {
        public static ToolExecutionResult succeeded(String rawOutput) {
            return new ToolExecutionResult(ToolExecutionStatus.SUCCEEDED, rawOutput == null ? "" : rawOutput, "");
        }

        public static ToolExecutionResult retryScheduled(String message) {
            return new ToolExecutionResult(ToolExecutionStatus.RETRY_SCHEDULED, "", message == null ? "" : message);
        }

        public static ToolExecutionResult fastFailed(String message) {
            return new ToolExecutionResult(ToolExecutionStatus.FAST_FAILED, "", message == null ? "" : message);
        }
    }

    public enum ToolExecutionStatus {
        SUCCEEDED,
        RETRY_SCHEDULED,
        FAST_FAILED
    }

    private String invokeRealMcpTool(String intent,
                                     String skillName,
                                     AssistantPlannedTool tool,
                                     String argsJson) throws Exception {
        String serverUrl = normalizeMcpServerUrl(text(tool.getServerUrl()));

        JsonNode argsNode;
        try {
            argsNode = objectMapper.readTree(blank(argsJson) ? "{}" : argsJson);
        } catch (Exception ignore) {
            argsNode = objectMapper.createObjectNode();
        }

        var req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", "tool-call-" + System.currentTimeMillis());
        req.put("method", "tools/call");
        var params = objectMapper.createObjectNode();
        params.put("name", text(tool.getToolName()));
        params.set("arguments", argsNode);
        req.set("params", params);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", ZhipuHttpProtocol.bearerValue(apiKey));

        String responseBody = httpRequestClient.post(serverUrl, objectMapper.writeValueAsString(req), headers);
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String msg = text(error.path("message").asText(""));
            if (msg.isEmpty()) {
                msg = error.toString();
            }
            throw new IllegalStateException("mcp tools/call error: " + msg);
        }

        String output = extractToolOutputFromJsonRpc(root.path("result"));
        if (blank(output)) {
            throw new IllegalStateException("mcp tools/call empty result");
        }
        return output;
    }

    private String extractToolOutputFromJsonRpc(JsonNode result) {
        if (result == null || result.isMissingNode() || result.isNull()) {
            return "";
        }

        JsonNode structured = result.path("structuredContent");
        if (!structured.isMissingNode() && !structured.isNull()) {
            return structured.toString();
        }

        JsonNode content = result.path("content");
        if (content.isArray()) {
            StringBuilder plain = new StringBuilder();
            for (JsonNode item : content) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String textPayload = firstTextLike(item, "text", "content", "data", "value");
                if (!blank(textPayload)) {
                    String primary = extractPrimaryPayload(textPayload);
                    if (!blank(primary)) {
                        return primary;
                    }
                    if (plain.length() > 0) {
                        plain.append('\n');
                    }
                    plain.append(cleanDocNoise(textPayload));
                    continue;
                }
                if (item.isObject() || item.isArray()) {
                    String primary = extractPrimaryPayload(item.toString());
                    if (!blank(primary)) {
                        return primary;
                    }
                    if (plain.length() > 0) {
                        plain.append('\n');
                    }
                    plain.append(item);
                }
            }
            if (plain.length() > 0) {
                return plain.toString();
            }
        }

        String direct = firstTextLike(result, "output", "result", "content", "text", "data");
        if (!blank(direct)) {
            String primary = extractPrimaryPayload(direct);
            if (!blank(primary)) {
                return primary;
            }
            return cleanDocNoise(direct);
        }

        return result.toString();
    }

    private String extractPrimaryPayload(String raw) {
        String text = cleanDocNoise(raw);
        if (blank(text)) {
            return "";
        }
        String parsedWhole = tryNormalizeJson(text);
        if (!blank(parsedWhole)) {
            return parsedWhole;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String maybe = text.substring(start, end + 1);
            String parsed = tryNormalizeJson(maybe);
            if (!blank(parsed)) {
                return parsed;
            }
        }
        return text;
    }

    private String tryNormalizeJson(String maybeJson) {
        try {
            JsonNode parsed = objectMapper.readTree(maybeJson);
            scrubMetaFields(parsed);
            return parsed.toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    private void scrubMetaFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                scrubMetaFields(item);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        List<String> toRemove = new ArrayList<>();
        obj.fieldNames().forEachRemaining(field -> {
            if (isMetaKey(field)) {
                toRemove.add(field);
            }
        });
        for (String key : toRemove) {
            obj.remove(key);
        }
        obj.fields().forEachRemaining(entry -> scrubMetaFields(entry.getValue()));
    }

    private String cleanDocNoise(String raw) {
        if (blank(raw)) {
            return "";
        }
        String text = raw.trim();
        String[] markers = new String[]{
                "以下是返回参数说明",
                "参数名称:",
                "参数类型:",
                "参数描述:",
                "参数示例:"
        };
        int cut = -1;
        for (String marker : markers) {
            int idx = text.indexOf(marker);
            if (idx >= 0 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut > 0) {
            text = text.substring(0, cut).trim();
        }
        return text;
    }

    private String firstTextLike(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (v.isMissingNode() || v.isNull()) {
                continue;
            }
            if (v.isTextual()) {
                String text = v.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            } else if (v.isObject() || v.isArray()) {
                return v.toString();
            }
        }
        return "";
    }

    private String normalizeMcpServerUrl(String serverUrl) {
        if (blank(serverUrl)) {
            return "";
        }
        String resolved = serverUrl
                .replace("YOUR_ZHIPU_API_KEY", text(apiKey))
                .replace("${ZHIPU_API_KEY}", text(apiKey))
                .replace("{ZHIPU_API_KEY}", text(apiKey));
        return stripQueryParam(resolved, "Authorization");
    }

    private String stripQueryParam(String url, String key) {
        if (blank(url) || blank(key) || !url.contains("?")) {
            return url;
        }
        int idx = url.indexOf('?');
        String base = url.substring(0, idx);
        String query = url.substring(idx + 1);
        String[] parts = query.split("&");
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (blank(p)) {
                continue;
            }
            int eq = p.indexOf('=');
            String name = eq >= 0 ? p.substring(0, eq) : p;
            if (name.equalsIgnoreCase(key)) {
                continue;
            }
            kept.add(p);
        }
        if (kept.isEmpty()) {
            return base;
        }
        return base + "?" + String.join("&", kept);
    }
    public String summarizeToolOutput(String intent, String skillName, String toolName, String toolOutput) {
        try {
            String system = """
                    You summarize tool output in one short sentence.
                    Output plain text only.
                    """;
            String user = "intent=" + intent + "\nskill=" + skillName + "\ntool=" + toolName + "\noutput=" + toolOutput;
            return chatJson(system, user).replace("\n", " ").trim();
        } catch (Exception e) {
            log.warn("summarize tool output failed", e);
            return toolOutput == null ? "" : toolOutput;
        }
    }

    public ToolOutputAssessment analyzeToolOutput(String intent,
                                                  String skillName,
                                                  String toolName,
                                                  String toolOutput,
                                                  List<String> expectedCandidateSlots) {
        List<String> candidates = expectedCandidateSlots == null ? List.of() : expectedCandidateSlots.stream()
                .map(this::text)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        boolean parameterMissing = isParameterMissingOutput(toolOutput);
        if (parameterMissing) {
            List<String> suggested = suggestMissingSlotsFromOutput(toolOutput, candidates);
            String failureReason = inferFailureReason(toolOutput);
            return new ToolOutputAssessment("", true, suggested, failureReason);
        }

        try {
            String system = getToolOutputAnalyzeSystemPrompt();
            String user = new StringBuffer(256)
                    .append("intent=").append(text(intent))
                    .append("\nskill=").append(text(skillName))
                    .append("\ntool=").append(text(toolName))
                    .append("\noutput=").append(text(toolOutput))
                    .toString();
            JsonNode parsed = tryParseJson(chatJson(system, user));
            String summary = text(parsed.path("summary").asText(""));
            if (summary.isEmpty()) {
                summary = summarizeToolOutput(intent, skillName, toolName, toolOutput);
            }
            return new ToolOutputAssessment(summary, false, List.of(), "");
        } catch (Exception e) {
            log.warn("analyze tool output failed", e);
            return new ToolOutputAssessment(
                    summarizeToolOutput(intent, skillName, toolName, toolOutput),
                    false,
                    List.of(),
                    ""
            );
        }
    }

    private boolean isParameterMissingOutput(String output) {
        String text = safeLower(output);
        if (text.isEmpty()) {
            return false;
        }
        String[] keywords = new String[]{
                "missing required",
                "required argument",
                "missing argument",
                "missing parameter",
                "invalid argument",
                "invalid parameter",
                "parameter error",
                "validation failed",
                "invalid format",
                "参数缺失",
                "缺少参数",
                "必填参数",
                "参数错误",
                "参数不合法",
                "参数校验失败"
        };
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> suggestMissingSlotsFromOutput(String output, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String text = safeLower(output);
        List<String> hits = new ArrayList<>();
        for (String slot : candidates) {
            String s = text(slot).toLowerCase();
            if (s.isEmpty()) {
                continue;
            }
            String compact = s.replace("_", "");
            if (text.contains(s) || (!compact.equals(s) && text.contains(compact))) {
                hits.add(slot);
            }
        }
        if (!hits.isEmpty()) {
            return hits.stream().distinct().toList();
        }
        return candidates;
    }

    private String inferFailureReason(String output) {
        String text = text(output);
        if (text.isEmpty()) {
            return "parameter missing or invalid";
        }
        if (text.length() > 120) {
            return text.substring(0, 120);
        }
        return text;
    }

    private String safeLower(String value) {
        String s = text(value);
        return s.isEmpty() ? "" : s.toLowerCase();
    }

    public Map<String, String> extractSlotsFromToolRawOutput(String intent,
                                                             String skillName,
                                                             String rawOutput,
                                                             List<String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return Map.of();
        }
        Map<String, String> deterministic = extractSlotsDeterministically(rawOutput, missingSlots);
        List<String> left = missingSlots.stream()
                .map(this::text)
                .filter(s -> !s.isEmpty())
                .filter(s -> !deterministic.containsKey(s))
                .toList();
        if (left.isEmpty()) {
            return deterministic;
        }
        try {
            String system = """
                    You extract slots from tool raw output. Output JSON only.
                    JSON schema: {"entities":[{"name":"slotKey","value":"slotValue"}]}
                    Rules: name must be from missingSlots only.
                    """;
            String missing = objectMapper.writeValueAsString(left);
            String user = "intent=" + intent + "\nskill=" + skillName + "\nmissingSlots=" + missing + "\nrawOutput=" + rawOutput;
            JsonNode parsed = tryParseJson(chatJson(system, user));
            Map<String, String> llm = parseEntities(parsed.path("entities"), new LinkedHashSet<>(left));
            Map<String, String> merged = new LinkedHashMap<>(deterministic);
            llm.forEach((k, v) -> {
                if (isValidExtractedValue(k, v, rawOutput)) {
                    merged.put(k, v);
                }
            });
            return merged;
        } catch (Exception e) {
            log.warn("extract slots from tool raw output failed", e);
            return deterministic;
        }
    }

    private Map<String, String> extractSlotsDeterministically(String rawOutput, List<String> missingSlots) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode root = tryParseJson(rawOutput);
        if (root == null || root.isMissingNode() || root.isNull() || root.isEmpty()) {
            return out;
        }
        // 通用规则：先移除链路元字段，避免污染业务槽位抽取。
        scrubMetaFields(root);
        Map<String, List<String>> keyToValues = new LinkedHashMap<>();
        collectLeafTextByKey(root, keyToValues);
        for (String slot : missingSlots) {
            String slotKey = text(slot);
            if (slotKey.isEmpty()) {
                continue;
            }
            List<String> aliases = aliasesForSlot(slotKey);
            String value = pickFirstByAliases(keyToValues, aliases, true);
            if (!value.isEmpty() && isValidExtractedValue(slotKey, value, rawOutput)) {
                out.put(slotKey, value);
            }
        }
        return out;
    }

    private void collectLeafTextByKey(JsonNode node, Map<String, List<String>> keyToValues) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = text(entry.getKey()).toLowerCase();
                JsonNode value = entry.getValue();
                if (value != null && (value.isValueNode())) {
                    String v = text(value.asText(""));
                    if (!v.isEmpty()) {
                        keyToValues.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                    }
                } else {
                    collectLeafTextByKey(value, keyToValues);
                }
            });
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectLeafTextByKey(item, keyToValues);
            }
        }
    }

    private List<String> aliasesForSlot(String slotKey) {
        String k = text(slotKey).toLowerCase();
        if (k.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(k);
        out.add(normalizeKey(k));
        List<String> tokens = splitKeyTokens(k);
        if (!tokens.isEmpty()) {
            out.add(String.join("_", tokens));
            out.add(String.join("", tokens));
            out.add(String.join("-", tokens));
        }
        return out.stream().filter(s -> !s.isBlank()).toList();
    }

    private String pickFirstByAliases(Map<String, List<String>> keyToValues,
                                      List<String> aliases,
                                      boolean skipMetaKeys) {
        for (String alias : aliases) {
            String a = text(alias).toLowerCase();
            if (a.isEmpty()) {
                continue;
            }
            if (skipMetaKeys && isMetaKey(a)) {
                continue;
            }
            String na = normalizeKey(a);
            for (Map.Entry<String, List<String>> entry : keyToValues.entrySet()) {
                String sourceKey = text(entry.getKey()).toLowerCase();
                if (skipMetaKeys && isMetaKey(sourceKey)) {
                    continue;
                }
                if (!sourceKey.equals(a) && !normalizeKey(sourceKey).equals(na)) {
                    continue;
                }
                List<String> values = entry.getValue();
                if (values == null) {
                    continue;
                }
                for (String v : values) {
                    String value = text(v);
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private boolean isMetaKey(String key) {
        String k = text(key).toLowerCase();
        return k.equals("taskno")
                || k.equals("requestid")
                || k.equals("traceid")
                || k.equals("jobid")
                || k.equals("taskid");
    }

    private boolean isValidExtractedValue(String slotKey, String value, String rawOutput) {
        String v = text(value);
        if (v.isEmpty()) {
            return false;
        }
        String slot = text(slotKey).toLowerCase();
        if (isMetaKey(slot)) {
            return false;
        }
        return true;
    }

    private List<String> splitKeyTokens(String key) {
        String cleaned = text(key)
                .replace('-', '_')
                .replace(' ', '_');
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String withCamelBreak = cleaned
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase();
        String[] arr = withCamelBreak.split("_+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            String t = text(s).toLowerCase();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private String normalizeKey(String key) {
        return text(key).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public String renderFinalAnswer(String intent, String skillName, Map<String, String> toolOutputSummaries) {
        try {
            String system = """
                    You are a final answer generator.
                    Use executed tool summaries and answer briefly.
                    Do not fabricate facts.
                    """;
            String summaryJson = objectMapper.writeValueAsString(toolOutputSummaries == null ? Map.of() : toolOutputSummaries);
            String user = "intent=" + intent + "\nskill=" + skillName + "\nexecutedToolSummaries=" + summaryJson;
            String answer = chatJson(system, user).trim();
            return answer.isEmpty() ? "Tool execution completed." : answer;
        } catch (Exception e) {
            log.warn("render final answer failed", e);
            return "Tool execution completed.";
        }
    }

    /**
     * 閺嶈宓佸銉ュ徔 inputSlots 鐏忓棗鐤勬担鎾冲敶鐎涙ɑ妲х亸鍕礋瀹搞儱鍙块崗銉ュ棘閿涙矮绱崗鍫滃▏閻?field 娴ｆ粈璐熼崣鍌涙殶閸氬稄绱濈紓铏规阜閸ョ偤鈧偓 slotKey閵?     */
    private Map<String, String> buildToolArgs(AssistantPlannedTool tool, Map<String, String> entities) {
        Map<String, String> args = new LinkedHashMap<>();
        if (tool == null) {
            return args;
        }
        List<InputSlot> slots = tool.getInputSlots() == null ? List.of() : tool.getInputSlots();
        for (InputSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            String slotKey = text(slot.getSlotKey());
            String field = text(slot.getField());
            String value = entities == null ? "" : text(entities.get(slotKey));
            if (blank(value)) {
                continue;
            }
            args.put(blank(field) ? slotKey : field, value);
        }
        if (!args.isEmpty()) {
            return args;
        }
        List<String> keys = new ArrayList<>();
        if (tool.getRequiredSlots() != null) {
            keys.addAll(tool.getRequiredSlots());
        }
        if (tool.getOptionalSlots() != null) {
            keys.addAll(tool.getOptionalSlots());
        }
        for (String key : keys) {
            String k = text(key);
            String v = entities == null ? "" : text(entities.get(k));
            if (!blank(k) && !blank(v)) {
                args.put(k, v);
            }
        }
        return args;
    }

    private String safeBuildArgsJson(AssistantPlannedTool tool, Map<String, String> entities) {
        try {
            return objectMapper.writeValueAsString(buildToolArgs(tool, entities));
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private Throwable unwrapExecutionException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? throwable : current;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ExecutorSaturatedException
                    || current instanceof RejectedExecutionException
                    || current instanceof TimeoutException) {
                return true;
            }
            String msg = safeMessage(current).toLowerCase();
            if (msg.contains("http post request failed")
                    || msg.contains("http request failed with status")
                    || msg.contains("status:")
                    || msg.contains("timeout")
                    || msg.contains("timed out")
                    || msg.contains("429")
                    || msg.contains("too many")
                    || msg.contains("rate limit")
                    || msg.contains("503")
                    || msg.contains("502")
                    || msg.contains("connection reset")
                    || msg.contains("connection refused")
                    || msg.contains("temporarily")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String msg = throwable.getMessage();
        if (msg == null || msg.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return msg;
    }

    private String chatJson(String systemPrompt, String userPrompt) throws Exception {
        String requestBody = objectMapper.createObjectNode()
                .put("model", slotModel)
                .put("temperature", 0)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt))
                        .add(objectMapper.createObjectNode().put("role", "user").put("content", userPrompt)))
                .toString();
        HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                        baseUrl, ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH, apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        String response = httpRequestClient.send(llmHttpClientRouter.getClient(slotModel), request);
        JsonNode root = objectMapper.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String buildContext(List<AssistantDialogueService.DialogueMessage> recentContext, String userMessage) {
        String history = recentContext == null ? "" : recentContext.stream()
                .map(m -> m.getRole() + ":" + m.getContent())
                .collect(Collectors.joining("\n"));
        if (history.isBlank()) {
            return "user:" + userMessage;
        }
        return history + "\nuser:" + userMessage;
    }

    private JsonNode tryParseJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String maybeJson = content.substring(start, end + 1);
                try {
                    return objectMapper.readTree(maybeJson);
                } catch (Exception ignoredAgain) {
                    return objectMapper.createObjectNode();
                }
            }
            return objectMapper.createObjectNode();
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> resolveHardRequiredSlots(AssistantPlannedTool tool) {
        if (tool == null) {
            return List.of();
        }
        List<InputSlot> slots = tool.getInputSlots() == null ? List.of() : tool.getInputSlots();
        if (slots.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (InputSlot slot : slots) {
            if (slot == null || blank(slot.getSlotKey())) {
                continue;
            }
            String requirement = text(slot.getRequirement()).toUpperCase();
            if ("HARD_REQUIRED".equals(requirement) && !out.contains(slot.getSlotKey())) {
                out.add(slot.getSlotKey());
            }
        }
        return out;
    }

    private List<String> resolveOptionalSlots(AssistantPlannedTool tool) {
        if (tool == null) {
            return List.of();
        }
        List<InputSlot> slots = tool.getInputSlots() == null ? List.of() : tool.getInputSlots();
        if (!slots.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (InputSlot slot : slots) {
                if (slot == null || blank(slot.getSlotKey())) {
                    continue;
                }
                String requirement = text(slot.getRequirement()).toUpperCase();
                boolean hard = "HARD_REQUIRED".equals(requirement);
                if (!hard && !out.contains(slot.getSlotKey())) {
                    out.add(slot.getSlotKey());
                }
            }
            return out;
        }
        return List.of();
    }

    private boolean blank(String value) {
        return text(value).isEmpty();
    }

    private Map<String, String> parseEntities(JsonNode entitiesNode, Set<String> slotWhitelist) {
        Map<String, String> entities = new LinkedHashMap<>();
        if (!entitiesNode.isArray()) {
            return entities;
        }
        boolean hasWhitelist = slotWhitelist != null && !slotWhitelist.isEmpty();
        for (JsonNode node : entitiesNode) {
            String name = node.path("name").asText("").trim();
            String value = node.path("value").asText("").trim();
            if (hasWhitelist && !slotWhitelist.contains(name)) {
                continue;
            }
            if (!name.isEmpty() && !value.isEmpty()) {
                entities.put(name, value);
            }
        }
        return entities;
    }

    private Set<String> getGlobalSlotWhitelist() {
        Set<String> cache = globalSlotWhitelistCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (globalSlotWhitelistCache != null) {
                return globalSlotWhitelistCache;
            }
            Set<String> loaded = loadGlobalSlotWhitelist();
            globalSlotWhitelistCache = loaded;
            log.info("loaded assistant global slot whitelist, size={}", loaded.size());
            return loaded;
        }
    }

    private Set<String> loadGlobalSlotWhitelist() {
        Set<String> fromJson = tryLoadWhitelistFromJsonList(intentSlotWhitelistPath);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }

        List<String> candidates = new ArrayList<>();
        addIfNotBlank(candidates, slotSummaryPath);
        candidates.add("file:./dataset/mcp_final_summary.json");
        candidates.add("file:../dataset/mcp_final_summary.json");
        candidates.add("dataset/mcp_final_summary.json");
        candidates.add("../dataset/mcp_final_summary.json");

        for (String location : candidates) {
            try {
                List<McpSkill> skills = tryLoadSkills(location);
                if (skills == null || skills.isEmpty()) {
                    continue;
                }
                Set<String> whitelist = extractSlotKeys(skills);
                if (!whitelist.isEmpty()) {
                    return whitelist;
                }
            } catch (Exception e) {
                log.warn("failed to load slot whitelist from [{}], try next", location, e);
            }
        }
        return Set.of();
    }

    private Set<String> tryLoadWhitelistFromJsonList(String path) {
        if (path == null || path.isBlank()) {
            return Set.of();
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            JsonNode root;
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    root = objectMapper.readTree(inputStream);
                }
            } else {
                root = objectMapper.readTree(new File(path));
            }
            if (root == null || !root.isArray()) {
                return Set.of();
            }
            Set<String> slots = new LinkedHashSet<>();
            for (JsonNode item : root) {
                if (item == null) {
                    continue;
                }
                if (item.isTextual()) {
                    String key = item.asText("").trim();
                    if (!key.isEmpty()) {
                        slots.add(key);
                    }
                    continue;
                }
                String slotKey = item.path("slotKey").asText("").trim();
                if (slotKey.isEmpty()) {
                    slotKey = item.path("name").asText("").trim();
                }
                if (!slotKey.isEmpty()) {
                    slots.add(slotKey);
                }
            }
            return slots;
        } catch (Exception e) {
            log.warn("failed to load intent slot whitelist from [{}]", path, e);
            return Set.of();
        }
    }

    private List<McpSkill> tryLoadSkills(String location) throws Exception {
        if (location == null || location.isBlank()) {
            return List.of();
        }
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            try (InputStream inputStream = resource.getInputStream()) {
                return McpJsonParser.parseFromStream(inputStream);
            }
        }
        return McpJsonParser.parseFromFile(location);
    }

    private Set<String> extractSlotKeys(List<McpSkill> skills) {
        Set<String> keys = new LinkedHashSet<>();
        for (McpSkill skill : skills) {
            if (skill == null || skill.getTools() == null) {
                continue;
            }
            for (McpTool tool : skill.getTools()) {
                if (tool == null || tool.getInputSlots() == null) {
                    continue;
                }
                for (InputSlot slot : tool.getInputSlots()) {
                    if (slot == null || slot.getSlotKey() == null) {
                        continue;
                    }
                    String slotKey = slot.getSlotKey().trim();
                    if (!slotKey.isEmpty()) {
                        keys.add(slotKey);
                    }
                }
            }
        }
        return keys;
    }

    private void addIfNotBlank(List<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value.trim());
        }
    }

    private String getToolOutputAnalyzeSystemPrompt() {
        String cache = toolOutputAnalyzeSystemPromptCache;
        if (cache != null && !cache.isBlank()) {
            return cache;
        }
        synchronized (this) {
            if (toolOutputAnalyzeSystemPromptCache != null && !toolOutputAnalyzeSystemPromptCache.isBlank()) {
                return toolOutputAnalyzeSystemPromptCache;
            }
            try {
                Resource resource = resourceLoader.getResource(toolOutputAnalyzePromptPath);
                if (!resource.exists()) {
                    throw new IllegalStateException("prompt file not found: " + toolOutputAnalyzePromptPath);
                }
                try (InputStream is = resource.getInputStream()) {
                    String prompt = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (prompt.isEmpty()) {
                        throw new IllegalStateException("prompt file is empty: " + toolOutputAnalyzePromptPath);
                    }
                    toolOutputAnalyzeSystemPromptCache = prompt;
                    return prompt;
                }
            } catch (Exception e) {
                throw new IllegalStateException("failed to load tool output analyze prompt: " + toolOutputAnalyzePromptPath, e);
            }
        }
    }

    public record ToolOutputAssessment(String summary,
                                       boolean parameterMissing,
                                       List<String> suggestedMissingSlots,
                                       String failureReason) {
    }

    public record IntentDriftDecision(boolean intentDrift, String reason) {
    }
}
