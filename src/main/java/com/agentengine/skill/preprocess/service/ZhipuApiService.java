package com.agentengine.skill.preprocess.service;

import com.agentengine.intent.model.ActionType;
import com.agentengine.intent.model.IntentTag;
import com.agentengine.skill.preprocess.aop.CheckRequestAlive;
import com.agentengine.skill.preprocess.config.ZhipuProperties;
import com.agentengine.skill.preprocess.model.CleanedToolSemantic;
import com.agentengine.skill.preprocess.model.SkillLabelPrediction;
import com.agentengine.skill.preprocess.model.ToolDescriptor;
import com.agentengine.skill.preprocess.util.ServerLabelExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class ZhipuApiService {

    private static final Logger log = LoggerFactory.getLogger(ZhipuApiService.class);

    private final ZhipuProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, Semaphore> modelSemaphores = new ConcurrentHashMap<>();
    private final Map<String, BreakerState> modelBreakers = new ConcurrentHashMap<>();

    public ZhipuApiService(ZhipuProperties properties, ObjectMapper objectMapper, HttpClient zhipuHttpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = zhipuHttpClient;
        modelSemaphores.put(properties.getDiscoveryModel(), new Semaphore(Math.max(1, properties.getDiscoveryMaxInflight())));
        modelSemaphores.put(properties.getSemanticModel(), new Semaphore(Math.max(1, properties.getSemanticMaxInflight())));
        modelSemaphores.put(properties.getLabelModel(), new Semaphore(Math.max(1, properties.getLabelMaxInflight())));
        modelSemaphores.put(properties.getEmbeddingModel(), new Semaphore(Math.max(1, properties.getEmbeddingMaxInflight())));
    }

    @CheckRequestAlive
    public List<ToolDescriptor> fetchRawToolsFromMcp(String curlExample) {
        return executeWithModelGuard(properties.getDiscoveryModel(), () -> {
            ensureApiKey();
            String mcpServerUrl = parseServerUrlFromMcpJsonOrThrow(curlExample);
            JsonNode normalizedToolsNode = buildMcpToolsConfigNode(curlExample);

            Map<String, Object> req = new HashMap<>();
            req.put("model", properties.getDiscoveryModel());
            req.put("stream", false);
            req.put("temperature", 0.1);
            req.put("messages", List.of(
                    Map.of("role", "user", "content", "Please call mcp_list_tools and return raw tool name, description, input_schema without rewriting.")
            ));
            req.put("tools", objectMapper.convertValue(normalizedToolsNode, new TypeReference<List<Map<String, Object>>>() {
            }));

            JsonNode resp = callJsonApi("/chat/completions", req);
            List<ToolDescriptor> out = extractToolDescriptorsFromModelResponse(resp);
            if (out.isEmpty()) {
                String snippet = resp.path("choices").isArray() && !resp.path("choices").isEmpty()
                        ? resp.path("choices").get(0).path("message").toString()
                        : resp.toString();
                if (snippet.length() > 800) {
                    snippet = snippet.substring(0, 800) + "...";
                }
                throw new IllegalStateException("no tools parsed from mcp_list_tools output, message=" + snippet);
            }
            return out.stream()
                    .map(t -> new ToolDescriptor(t.name(), t.description(), t.inputSchema(), mcpServerUrl))
                    .toList();
        });
    }

    private JsonNode buildMcpToolsConfigNode(String curlOrUrl) {
        String input = curlOrUrl == null ? "" : curlOrUrl.trim();
        if (input.startsWith("http://") || input.startsWith("https://")) {
            String skillName = ServerLabelExtractor.fromServerUrl(input);
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode tool = objectMapper.createObjectNode();
            ObjectNode mcp = objectMapper.createObjectNode();
            mcp.put("transport_type", "streamable-http");
            mcp.put("server_label", skillName);
            mcp.put("server_url", input);
            ObjectNode headers = objectMapper.createObjectNode();
            headers.put("Authorization", "Bearer " + properties.getApiKey());
            mcp.set("headers", headers);
            tool.set("mcp", mcp);
            tool.put("type", "mcp");
            tools.add(tool);
            return tools;
        }

        String serverUrlFromJson = parseServerUrlFromMcpJsonOrThrow(input);
        String skillName = ServerLabelExtractor.fromServerUrl(serverUrlFromJson);
        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode tool = objectMapper.createObjectNode();
        ObjectNode mcp = objectMapper.createObjectNode();
        mcp.put("transport_type", "streamable-http");
        mcp.put("server_label", skillName);
        mcp.put("server_url", serverUrlFromJson);
        ObjectNode headers = objectMapper.createObjectNode();
        headers.put("Authorization", "Bearer " + properties.getApiKey());
        mcp.set("headers", headers);
        tool.set("mcp", mcp);
        tool.put("type", "mcp");
        tools.add(tool);
        return tools;
    }

    public String parseServerLabelFromCurlOrUrl(String curlExample) {
        String raw = curlExample == null ? "" : curlExample.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return ServerLabelExtractor.fromServerUrl(raw);
        }
        String serverUrl = parseServerUrlFromMcpJsonOrThrow(raw);
        return ServerLabelExtractor.fromServerUrl(serverUrl);
    }

    public String parseServerUrlFromCurlOrUrl(String curlExample) {
        String raw = curlExample == null ? "" : curlExample.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        return parseServerUrlFromMcpJsonOrThrow(raw);
    }

    public String parseServerUrlFromMcpJsonOrThrow(String rawJson) {
        JsonNode root = tryReadTree(rawJson);
        if (root == null) {
            throw new IllegalArgumentException("mcp json invalid");
        }
        JsonNode mcpServers = root.path("mcpServers");
        if (!mcpServers.isObject()) {
            throw new IllegalArgumentException("mcp json invalid: missing mcpServers");
        }
        var fields = mcpServers.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode serverNode = entry.getValue();
            String url = serverNode.path("url").asText("").trim();
            if (!url.isBlank()) {
                return url;
            }
        }
        throw new IllegalArgumentException("mcp json invalid: missing url");
    }

    private String findFirstUrlNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isObject()) {
            JsonNode urlNode = node.get("url");
            if (urlNode != null && urlNode.isTextual() && urlNode.asText("").startsWith("http")) {
                return urlNode.asText("");
            }
            JsonNode serverUrlNode = node.get("server_url");
            if (serverUrlNode != null && serverUrlNode.isTextual() && serverUrlNode.asText("").startsWith("http")) {
                return serverUrlNode.asText("");
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String found = findFirstUrlNode(entry.getValue());
                if (!found.isBlank()) {
                    return found;
                }
            }
            return "";
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findFirstUrlNode(item);
                if (!found.isBlank()) {
                    return found;
                }
            }
            return "";
        }
        return "";
    }

    @CheckRequestAlive
    public double[] embedding(String text) {
        return executeWithModelGuard(properties.getEmbeddingModel(), () -> {
            ensureApiKey();
            Map<String, Object> req = Map.of(
                    "model", properties.getEmbeddingModel(),
                    "input", text
            );
            JsonNode resp = callJsonApi("/embeddings", req);
            JsonNode arr = resp.path("data");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new IllegalStateException("embedding response missing data");
            }
            JsonNode vecNode = arr.get(0).path("embedding");
            double[] vec = new double[vecNode.size()];
            for (int i = 0; i < vecNode.size(); i++) {
                vec[i] = vecNode.get(i).asDouble();
            }
            return vec;
        });
    }

    @CheckRequestAlive
    public SkillLabelPrediction classifySkillLabel(String prompt) {
        return executeWithModelGuard(properties.getLabelModel(), () -> {
            ensureApiKey();
            Map<String, Object> req = new HashMap<>();
            req.put("model", properties.getLabelModel());
            req.put("stream", false);
            req.put("temperature", 0.1);
            req.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            JsonNode resp = callJsonApi("/chat/completions", req);
            JsonNode choice = resp.path("choices").isArray() && !resp.path("choices").isEmpty() ? resp.path("choices").get(0) : null;
            if (choice == null) {
                return new SkillLabelPrediction(IntentTag.QUERY, ActionType.READ, 0.0);
            }
            String content = choice.path("message").path("content").asText("");
            JsonNode node = tryReadTree(content);
            if (node == null) {
                return new SkillLabelPrediction(IntentTag.QUERY, ActionType.READ, 0.0);
            }
            String intentStr = node.path("intentTag").asText("query");
            String actionStr = node.path("actionType").asText("read");
            double confidence = node.path("confidence").asDouble(0.0);
            IntentTag intentTag = IntentTag.fromCode(intentStr);
            ActionType actionType = ActionType.fromCode(actionStr);
            return new SkillLabelPrediction(intentTag, actionType, confidence);
        });
    }

    @CheckRequestAlive
    public CleanedToolSemantic cleanToolSemantic(String cleaningPrompt) {
        return executeWithModelGuard(properties.getSemanticModel(), () -> {
            ensureApiKey();
            Map<String, Object> req = new HashMap<>();
            req.put("model", properties.getSemanticModel());
            req.put("stream", false);
            req.put("temperature", 0.1);
            req.put("messages", List.of(
                    Map.of("role", "user", "content", cleaningPrompt)
            ));
            JsonNode resp = callJsonApi("/chat/completions", req);
            JsonNode choice = resp.path("choices").isArray() && !resp.path("choices").isEmpty() ? resp.path("choices").get(0) : null;
            if (choice == null) {
                return new CleanedToolSemantic("", "", "{}");
            }
            String content = choice.path("message").path("content").asText("");
            JsonNode node = tryReadTree(content);
            if (node == null) {
                return new CleanedToolSemantic("", "", "{}");
            }
            String toolName = node.path("tool_name").asText("");
            String embeddingText = node.path("embedding_text").asText("");
            return new CleanedToolSemantic(toolName, embeddingText, node.toString());
        });
    }

    @CheckRequestAlive
    public String generateSkillDescription(String prompt) {
        return executeWithModelGuard(properties.getLabelModel(), () -> {
            ensureApiKey();
            Map<String, Object> req = new HashMap<>();
            req.put("model", properties.getLabelModel());
            req.put("stream", false);
            req.put("temperature", 0.2);
            req.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            JsonNode resp = callJsonApi("/chat/completions", req);
            JsonNode choice = resp.path("choices").isArray() && !resp.path("choices").isEmpty() ? resp.path("choices").get(0) : null;
            if (choice == null) {
                return "";
            }
            return choice.path("message").path("content").asText("").trim();
        });
    }

    private <T> T executeWithModelGuard(String model, Supplier<T> action) {
        Semaphore semaphore = modelSemaphores.computeIfAbsent(model, m -> new Semaphore(1));
        BreakerState breaker = modelBreakers.computeIfAbsent(model, m -> new BreakerState());
        long now = System.currentTimeMillis();
        if (breaker.openUntilEpochMs > now) {
            throw new IllegalStateException("model circuit open: " + model);
        }
        boolean acquired;
        try {
            int timeoutMs = Math.max(0, properties.getModelAcquireTimeoutMs());
            acquired = timeoutMs == 0
                    ? semaphore.tryAcquire()
                    : semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model acquire interrupted: " + model, e);
        }
        if (!acquired) {
            throw new IllegalStateException("model inflight limit reached: " + model);
        }

        try {
            T result = action.get();
            breaker.onSuccess();
            return result;
        } catch (RuntimeException ex) {
            breaker.onFailure(properties.getBreakerFailureThreshold(), properties.getBreakerOpenMs());
            throw ex;
        } finally {
            semaphore.release();
        }
    }

    private static final class BreakerState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long openUntilEpochMs = 0L;

        private void onSuccess() {
            consecutiveFailures.set(0);
            openUntilEpochMs = 0L;
        }

        private void onFailure(int threshold, int openMs) {
            int failCount = consecutiveFailures.incrementAndGet();
            if (failCount >= Math.max(1, threshold)) {
                openUntilEpochMs = System.currentTimeMillis() + Math.max(1000, openMs);
                consecutiveFailures.set(0);
            }
        }
    }

    private JsonNode callJsonApi(String path, Object reqObj) {
        try {
            String reqBody = objectMapper.writeValueAsString(reqObj);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("zhipu request failed, status=" + response.statusCode() + ", body=" + response.body());
            }
            return readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("zhipu request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("zhipu request failed", e);
        }
    }

    private List<ToolDescriptor> extractToolDescriptorsFromModelResponse(JsonNode resp) {
        List<ToolDescriptor> tools = new ArrayList<>();
        JsonNode choice = resp.path("choices").isArray() && !resp.path("choices").isEmpty() ? resp.path("choices").get(0) : null;
        if (choice == null) {
            return tools;
        }
        JsonNode message = choice.path("message");
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode call : toolCalls) {
                JsonNode mcp = call.path("mcp");
                if (mcp != null && !mcp.isMissingNode() && !mcp.isNull()) {
                    // Some providers return tools directly under mcp.tools without type/output.
                    tools.addAll(extractToolDescriptorsFromNode(mcp.path("tools")));
                    tools.addAll(extractToolDescriptorsFromNode(mcp));
                }
            }
        }
        if (!tools.isEmpty()) {
            return tools;
        }

        String content = message.path("content").asText("");
        if (!content.isBlank()) {
            JsonNode contentNode = tryReadTree(trimToJsonBlock(content));
            if (contentNode != null) {
                tools.addAll(extractToolDescriptorsFromNode(contentNode));
            }
        }
        return tools;
    }

    private List<ToolDescriptor> extractToolDescriptorsFromNode(JsonNode output) {
        List<ToolDescriptor> tools = new ArrayList<>();
        if (output == null || output.isMissingNode() || output.isNull()) {
            return tools;
        }
        JsonNode normalized = output;
        if (output.isTextual()) {
            normalized = tryReadTree(trimToJsonBlock(output.asText()));
            if (normalized == null) {
                return tools;
            }
        }

        JsonNode toolArray = findToolArrayNode(normalized);
        if (!toolArray.isArray()) {
            toolArray = normalized.isArray() ? normalized : toolArray;
        }
        if (!toolArray.isArray()) {
            return tools;
        }
        for (JsonNode t : toolArray) {
            JsonNode itemNode = normalizeToolItemNode(t);
            if (itemNode == null || itemNode.isNull() || itemNode.isMissingNode()) {
                continue;
            }
            Map<String, Object> rawMap = objectMapper.convertValue(itemNode, new TypeReference<Map<String, Object>>() {
            });
            JsonNode toolNode = objectMapper.valueToTree(rawMap);

            String name = firstNonBlank(
                    readText(toolNode, "name"),
                    readText(toolNode, "tool_name"),
                    readText(toolNode, "id")
            );
            if (name.isBlank()) {
                continue;
            }

            String desc = firstNonBlank(
                    readText(toolNode, "description"),
                    readText(toolNode, "desc"),
                    readText(toolNode, "summary")
            );
            String inputSchema = normalizeInputSchemaToJson(toolNode);
            tools.add(new ToolDescriptor(name, desc, inputSchema, ""));
        }
        return tools;
    }

    private String extractJsonDataArg(String curlExample) {
        int idx = curlExample.indexOf("--data");
        if (idx < 0) {
            throw new IllegalArgumentException("curl example missing --data");
        }
        int firstQuote = -1;
        char quote = '\'';
        for (int i = idx; i < curlExample.length(); i++) {
            char c = curlExample.charAt(i);
            if (c == '\'' || c == '"') {
                firstQuote = i;
                quote = c;
                break;
            }
        }
        if (firstQuote < 0) {
            throw new IllegalArgumentException("json quote after --data not found");
        }
        int end = -1;
        for (int i = firstQuote + 1; i < curlExample.length(); i++) {
            if (curlExample.charAt(i) == quote && curlExample.charAt(i - 1) != '\\') {
                end = i;
                break;
            }
        }
        if (end < 0) {
            throw new IllegalArgumentException("curl json is not closed");
        }
        return curlExample.substring(firstQuote + 1, end).trim();
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("json parse failed", e);
        }
    }

    private JsonNode normalizeMcpHeadersWithConfigApiKey(JsonNode toolsNode) {
        if (!toolsNode.isArray()) {
            return toolsNode;
        }
        ArrayNode copied = toolsNode.deepCopy();
        for (JsonNode toolNode : copied) {
            if (!(toolNode instanceof ObjectNode toolObj)) {
                continue;
            }
            JsonNode mcpNode = toolObj.path("mcp");
            if (!(mcpNode instanceof ObjectNode mcpObj)) {
                continue;
            }
            ObjectNode headersObj;
            JsonNode headersNode = mcpObj.path("headers");
            if (headersNode instanceof ObjectNode existingHeaders) {
                headersObj = existingHeaders;
            } else {
                headersObj = objectMapper.createObjectNode();
                mcpObj.set("headers", headersObj);
            }
            headersObj.put("Authorization", "Bearer " + properties.getApiKey());
        }
        return copied;
    }

    private JsonNode tryReadTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            log.debug("ignore non-json content");
            return null;
        }
    }

    private JsonNode findToolArrayNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        JsonNode[] candidates = new JsonNode[]{
                node.path("tools"),
                node.path("tool_list"),
                node.path("list"),
                node.path("items"),
                node.path("data").path("tools"),
                node.path("data").path("list"),
                node.path("result").path("tools"),
                node.path("result").path("list")
        };
        for (JsonNode c : candidates) {
            if (c != null && c.isArray()) {
                return c;
            }
        }
        return node;
    }

    private String trimToJsonBlock(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\s*", "");
            s = s.replaceAll("\\s*```$", "");
        }
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start = -1;
        if (objStart >= 0 && arrStart >= 0) {
            start = Math.min(objStart, arrStart);
        } else if (objStart >= 0) {
            start = objStart;
        } else if (arrStart >= 0) {
            start = arrStart;
        }
        if (start < 0) {
            return s;
        }
        int objEnd = s.lastIndexOf('}');
        int arrEnd = s.lastIndexOf(']');
        int end = Math.max(objEnd, arrEnd);
        if (end <= start) {
            return s.substring(start);
        }
        return s.substring(start, end + 1);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private JsonNode normalizeToolItemNode(JsonNode t) {
        if (t == null || t.isNull() || t.isMissingNode()) {
            return null;
        }
        if (t.isTextual()) {
            return tryReadTree(trimToJsonBlock(t.asText()));
        }
        return t;
    }

    private String readText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        return v.asText("");
    }

    private String normalizeInputSchemaToJson(JsonNode toolNode) {
        JsonNode schemaNode = toolNode.path("input_schema");
        if (schemaNode.isMissingNode() || schemaNode.isNull()) {
            schemaNode = toolNode.path("inputSchema");
        }
        if (schemaNode.isMissingNode() || schemaNode.isNull()) {
            schemaNode = toolNode.path("parameters");
        }

        JsonNode normalized;
        if (schemaNode.isMissingNode() || schemaNode.isNull()) {
            normalized = objectMapper.createObjectNode();
        } else if (schemaNode.isObject()) {
            normalized = schemaNode.deepCopy();
        } else {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("raw", schemaNode);
            normalized = wrapper;
        }

        Set<String> required = collectRequiredParamNames(normalized);
        if (!required.isEmpty() && normalized.isObject() && !normalized.has("required")) {
            ArrayNode requiredNode = objectMapper.createArrayNode();
            for (String r : required) {
                requiredNode.add(r);
            }
            ((ObjectNode) normalized).set("required", requiredNode);
        }
        return normalized.toString();
    }

    private Set<String> collectRequiredParamNames(JsonNode schemaNode) {
        Set<String> required = new LinkedHashSet<>();
        if (schemaNode == null || schemaNode.isNull() || schemaNode.isMissingNode()) {
            return required;
        }

        JsonNode requiredNode = schemaNode.path("required");
        if (requiredNode.isArray()) {
            for (JsonNode r : requiredNode) {
                String v = r.asText("");
                if (!v.isBlank()) {
                    required.add(v);
                }
            }
        }

        JsonNode properties = schemaNode.path("properties");
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                JsonNode meta = entry.getValue();
                if (meta != null && meta.path("required").asBoolean(false)) {
                    required.add(entry.getKey());
                }
            });
        }
        return required;
    }

    private void ensureApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("zhipu.api-key is not configured");
        }
    }
}
