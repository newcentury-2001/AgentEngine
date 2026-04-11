package com.agentengine.web.assistant.service;

import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentcommon.mcp.parser.McpJsonParser;
import com.agentengine.web.assistant.model.AssistantInferenceResult;
import com.agentengine.web.assistant.model.LlmAgentState;
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
            你是实体提取器。只返回 JSON，不要任何额外文本。
            JSON schema:
            {
              "entities": [
                {"name":"槽位名","value":"槽位值"}
              ]
            }
            规则:
            1) 仅抽取结构化实体，不做意图判断。
            2) name 只能从这个槽位白名单中选择: %s
            3) 如果用户提到的信息不在白名单里，直接忽略。
            4) 无实体时返回空数组。
            """;

    private static final String INTENT_VOTE_SYSTEM_PROMPT = """
            你是意图投票器。只返回 JSON，不要任何额外文本。
            JSON schema:
            {
              "needTool": boolean,
              "answerReady": boolean,
              "toolName": string
            }
            规则:
            1) 对用户问题做内部多路判断后给出最终投票结论。
            2) 需要外部工具查询/计算时，needTool=true。
            3) 不需要工具可直接回答时，answerReady=true。
            4) toolName 无法判断时返回空字符串。
            """;

    private static final String CLARIFICATION_SLOT_SYSTEM_PROMPT = """
            你是缺槽补全提取器。只返回 JSON，不要任何额外文本。
            JSON schema:
            {
              "toolName": string,
              "missingSlots": string[],
              "answerReady": boolean,
              "entities": [{"name":"实体名","value":"实体值"}]
            }
            规则:
            1) 根据用户最新输入，尽量补齐槽位。
            2) 若还有未补齐槽位，missingSlots 返回剩余项，answerReady=false。
            3) 若已补齐，missingSlots 返回空数组，answerReady=true。
            4) entities 仅用于结构化记忆更新。
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
        String embeddingInput = buildContext(recentContext, userMessage);
        return AssistantInferenceResult.builder()
                .needTool(vote.isNeedTool())
                .answerReady(vote.isAnswerReady())
                .toolName(vote.getToolName())
                .missingSlots(List.of())
                .errorMessage(vote.getErrorMessage())
                .embeddingDim(doEmbedding(embeddingInput))
                .entityMemory(entities.getEntityMemory())
                .build();
    }

    public AssistantInferenceResult inferForSlotFill(LlmAgentState stage,
                                                     List<String> expectedMissingSlots,
                                                     String userMessage) {
        return doClarificationSlotExtraction(stage, expectedMissingSlots, userMessage);
    }

    private AssistantInferenceResult doIntentEntityExtraction(String userMessage) {
        // 仅使用当前用户输入抽取实体；并限制在全局槽位白名单内。
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

    private AssistantInferenceResult doClarificationSlotExtraction(LlmAgentState stage,
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
                    .embeddingDim(null)
                    .entityMemory(parseEntities(parsed.path("entities"), Set.of()))
                    .build();
        } catch (Exception e) {
            log.warn("clarification slot extraction failed, fallback to conservative result", e);
            return AssistantInferenceResult.builder()
                    .needTool(true)
                    .answerReady(false)
                    .toolName("")
                    .missingSlots(expectedMissingSlots == null ? List.of("slot_fill_failed") : expectedMissingSlots)
                    .embeddingDim(null)
                    .entityMemory(Map.of())
                    .build();
        }
    }

    private Integer doEmbedding(String text) {
        try {
            String requestBody = objectMapper.createObjectNode()
                    .put("model", embeddingModel)
                    .put("input", text)
                    .put("encoding_format", "float")
                    .toString();
            HttpRequest request = ZhipuHttpProtocol.authorizedJsonPostBuilder(
                            baseUrl, ZhipuHttpProtocol.EMBEDDINGS_PATH, apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            String response = httpRequestClient.send(llmHttpClientRouter.getClient(embeddingModel), request);
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            return embeddingNode.isArray() ? embeddingNode.size() : 0;
        } catch (Exception e) {
            log.warn("embedding failed for assistant input", e);
            return 0;
        }
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
