package com.agentengine.web.assistant.service;

import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;
import com.agentengine.web.assistant.model.AssistantPlannedTool;
import com.agentengine.web.assistant.model.AssistantInferenceResult;
import com.agentengine.web.assistant.service.retrieval.SkillVectorRecord;
import com.agentengine.web.assistant.service.stage.AssistantStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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


    private final LlmHttpClientRouter llmHttpClientRouter;
    private final HttpRequestClient httpRequestClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

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

    private volatile Set<String> globalSlotWhitelistCache;

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

    public String executePlannedTool(String intent,
                                     String skillName,
                                     AssistantPlannedTool tool,
                                     Map<String, String> entities) {
        try {
            if (tool == null || blank(tool.getServerUrl()) || blank(tool.getToolName())) {
                return "tool execution failed: missing serverUrl/toolName";
            }
            String argsJson = objectMapper.writeValueAsString(buildToolArgs(tool, entities));
            String system = """
                    You are a strict MCP tool caller.
                    Call the tool and return output only.
                    """;
            String user = "intent=" + text(intent)
                    + "\nskill=" + text(skillName)
                    + "\nserverUrl=" + text(tool.getServerUrl())
                    + "\ntoolName=" + text(tool.getToolName())
                    + "\narguments=" + argsJson;
            String content = chatJson(system, user);
            if (blank(content)) {
                return "tool execution failed: empty response";
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("execute planned tool failed. skill={}, tool={}", skillName, tool == null ? "" : tool.getToolName(), e);
            return "tool execution failed: " + text(e.getMessage());
        }
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

    public Map<String, String> extractSlotsFromToolSummary(String intent,
                                                           String skillName,
                                                           String summary,
                                                           List<String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return Map.of();
        }
        try {
            String system = """
                    You extract slots from tool summary. Output JSON only.
                    JSON schema: {"entities":[{"name":"slotKey","value":"slotValue"}]}
                    Rules: name must be from missingSlots only.
                    """;
            String missing = objectMapper.writeValueAsString(missingSlots);
            String user = "intent=" + intent + "\nskill=" + skillName + "\nmissingSlots=" + missing + "\nsummary=" + summary;
            JsonNode parsed = tryParseJson(chatJson(system, user));
            return parseEntities(parsed.path("entities"), new LinkedHashSet<>(missingSlots));
        } catch (Exception e) {
            log.warn("extract slots from tool summary failed", e);
            return Map.of();
        }
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
            String fieldPath = text(slot.getFieldPath());
            String value = entities == null ? "" : text(entities.get(slotKey));
            if (blank(value)) {
                continue;
            }
            args.put(blank(fieldPath) ? slotKey : fieldPath, value);
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
        if (!slots.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (InputSlot slot : slots) {
                if (slot == null || blank(slot.getSlotKey())) {
                    continue;
                }
                String requirement = text(slot.getRequirement()).toUpperCase();
                boolean hard = "HARD_REQUIRED".equals(requirement)
                        || (requirement.isBlank() && slot.isRequired());
                if (hard && !out.contains(slot.getSlotKey())) {
                    out.add(slot.getSlotKey());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return tool.getRequiredSlots() == null ? List.of() : tool.getRequiredSlots();
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
                boolean hard = "HARD_REQUIRED".equals(requirement)
                        || (requirement.isBlank() && slot.isRequired());
                if (!hard && !out.contains(slot.getSlotKey())) {
                    out.add(slot.getSlotKey());
                }
            }
            return out;
        }
        return tool.getOptionalSlots() == null ? List.of() : tool.getOptionalSlots();
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
}
