package com.agentops.service;

import com.agentcommon.concurrent.NamedTaskRunnable;
import com.agentcommon.concurrent.TaskContext;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentops.config.OpsMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpToolListOpsService {

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{[\\s\\S]*?\\})\\s*```");

    private final OpsMcpProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService mcpIoExecutor;
    private final HttpRequestClient httpRequestClient;
    private final LlmHttpClientRouter llmHttpClientRouter;

    public McpToolListOpsService(
            OpsMcpProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("mcpIoExecutor") ExecutorService mcpIoExecutor,
            HttpRequestClient httpRequestClient,
            LlmHttpClientRouter llmHttpClientRouter
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mcpIoExecutor = mcpIoExecutor;
        this.httpRequestClient = httpRequestClient;
        this.llmHttpClientRouter = llmHttpClientRouter;
    }

    public Map<String, Object> exportToolsListToMarkdown() {
        ensureEnabledAndApiKey();
        Path sourcePath = resolvePath(properties.getSourceMarkdownPath());
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("source markdown not found: " + sourcePath);
        }

        List<ServerEntry> servers = parseServerEntries(sourcePath);
        List<ResultEntry> results = runBatchListTools(servers);
        Path outputPath = resolvePath(properties.getOutputMarkdownPath());
        Path outputJsonPath = resolvePath(properties.getOutputJsonPath());
        writeMarkdown(outputPath, results);
        writeJson(outputJsonPath, results);

        int ok = (int) results.stream().filter(ResultEntry::success).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "mcp tools exported");
        out.put("sourcePath", sourcePath.toString());
        out.put("outputPath", outputPath.toString());
        out.put("outputJsonPath", outputJsonPath.toString());
        out.put("totalServers", servers.size());
        out.put("successCount", ok);
        out.put("failedCount", results.size() - ok);
        return out;
    }

    public Map<String, Object> exportToolsListToMarkdown(List<Object> mcpObjects) {
        ensureEnabledAndApiKey();
        if (mcpObjects == null || mcpObjects.isEmpty()) {
            throw new IllegalArgumentException("request body mcp list is empty");
        }

        List<ServerEntry> servers = parseServerEntriesFromObjectList(mcpObjects);
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("no valid mcp server found in request body");
        }

        List<ResultEntry> results = runBatchListTools(servers);
        Path outputPath = resolvePath(properties.getOutputMarkdownPath());
        Path outputJsonPath = resolvePath(properties.getOutputJsonPath());
        writeMarkdown(outputPath, results);
        writeJson(outputJsonPath, results);

        int ok = (int) results.stream().filter(ResultEntry::success).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "mcp tools exported");
        out.put("sourcePath", "requestBody");
        out.put("outputPath", outputPath.toString());
        out.put("outputJsonPath", outputJsonPath.toString());
        out.put("totalServers", servers.size());
        out.put("successCount", ok);
        out.put("failedCount", results.size() - ok);
        return out;
    }

    private List<ResultEntry> runBatchListTools(List<ServerEntry> servers) {
        List<ResultEntry> results = new ArrayList<>(servers.size());
        int intervalMs = Math.max(1000, properties.getRequestIntervalMs());
        for (int i = 0; i < servers.size(); i++) {
            ServerEntry entry = servers.get(i);
            String taskName = "listTools:" + entry.serverLabel();
            CompletableFuture<ResultEntry> future = supplyAsyncNamed(taskName, () -> {
                try {
                    List<ToolItem> tools = listTools(entry.serverLabel(), entry.serverUrl());
                    return ResultEntry.success(entry.serverLabel(), entry.serverUrl(), tools);
                } catch (Exception e) {
                    return ResultEntry.failure(entry.serverLabel(), entry.serverUrl(), trim(e.getMessage(), 300));
                }
            });
            results.add(future.join());
            if (i < servers.size() - 1) {
                sleepQuietly(intervalMs);
            }
        }
        return results;
    }

    private List<ToolItem> listTools(String serverLabel, String serverUrl) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", safe(properties.getModel(), "glm-4-flash"));
        req.put("stream", false);
        req.put("temperature", 0.1);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", "Only call mcp_list_tools. Do not summarize and do not rewrite descriptions.");
        messages.add(user);
        req.set("messages", messages);

        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "mcp");
        ObjectNode mcp = objectMapper.createObjectNode();
        mcp.put("transport_type", resolveTransportType(serverUrl));
        mcp.put("server_label", serverLabel);
        mcp.put("server_url", serverUrl);
        ObjectNode headers = objectMapper.createObjectNode();
        headers.put("Authorization", ZhipuHttpProtocol.bearerValue(properties.getApiKey()));
        mcp.set("headers", headers);
        tool.set("mcp", mcp);
        tools.add(tool);
        req.set("tools", tools);

        Map<String, String> httpHeaders = ZhipuHttpProtocol.jsonHeaders(properties.getApiKey());

        String responseBody = httpRequestClient.post(
                llmHttpClientRouter.getClient(properties.getModel()),
                ZhipuHttpProtocol.endpoint(properties.getBaseUrl(), ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH),
                objectMapper.writeValueAsString(req),
                httpHeaders
        );


        if (!responseBody.startsWith("{") && !responseBody.startsWith("[")) {
            throw new IllegalStateException("http response not json: " + trim(responseBody, 300));
        }

        JsonNode root = objectMapper.readTree(responseBody);
        List<ToolItem> parsed = extractToolItemsFromToolCalls(root);
        if (parsed.isEmpty()) {
            parsed = extractToolItems(root);
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("no tools parsed");
        }
        return parsed;
    }

    private String resolveTransportType(String serverUrl) {
        String url = safeText(serverUrl).toLowerCase();
        if (url.contains("/sse") || url.contains("/sse?")) {
            return "sse";
        }
        return "streamable-http";
    }

    private List<ToolItem> extractToolItemsFromToolCalls(JsonNode root) {
        List<ToolItem> out = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        JsonNode choices = root.path("choices");
        if (!choices.isArray()) {
            return out;
        }
        for (JsonNode choice : choices) {
            JsonNode toolCalls = choice.path("message").path("tool_calls");
            if (!toolCalls.isArray()) {
                continue;
            }
            for (JsonNode call : toolCalls) {
                JsonNode tools = call.path("mcp").path("tools");
                if (!tools.isArray()) {
                    continue;
                }
                for (JsonNode t : tools) {
                    String name = text(t, "name", "tool_name", "id");
                    String description = text(t, "description", "tool_description");
                    JsonNode schema = t.get("input_schema");
                    String schemaText = schema == null || schema.isNull() ? "" : schema.toString();
                    if (name.isBlank()) {
                        continue;
                    }
                    String key = name + "||" + description + "||" + schemaText;
                    if (dedup.add(key)) {
                        out.add(new ToolItem(name, description, schemaText));
                    }
                }
            }
        }
        return out;
    }

    private List<ToolItem> extractToolItems(JsonNode root) {
        List<ToolItem> out = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        walk(root, out, dedup);
        return out;
    }

    private void walk(JsonNode node, List<ToolItem> out, Set<String> dedup) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String name = text(node, "name", "tool_name", "id");
            String description = text(node, "description", "tool_description");
            JsonNode schema = node.get("input_schema");
            if (!name.isBlank() && (!description.isBlank() || schema != null)) {
                String schemaText = schema == null ? "" : schema.toString();
                String key = name + "||" + description + "||" + schemaText;
                if (dedup.add(key)) {
                    out.add(new ToolItem(name, description, schemaText));
                }
            }
            node.fields().forEachRemaining(e -> walk(e.getValue(), out, dedup));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, out, dedup);
            }
        }
    }

    private String text(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isTextual()) {
                String s = v.asText("").trim();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return "";
    }

    private List<ServerEntry> parseServerEntries(Path sourcePath) {
        try {
            String md = Files.readString(sourcePath, StandardCharsets.UTF_8);
            Matcher matcher = JSON_BLOCK.matcher(md);
            List<ServerEntry> out = new ArrayList<>();
            Set<String> dedup = new LinkedHashSet<>();
            while (matcher.find()) {
                String json = matcher.group(1);
                try {
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode mcpServers = root.path("mcpServers");
                    if (!mcpServers.isObject()) {
                        continue;
                    }
                    mcpServers.fields().forEachRemaining(entry -> {
                        String label = entry.getKey();
                        String url = normalizeServerUrl(entry.getValue().path("url").asText(""));
                        addServerIfValid(label, url, dedup, out);
                    });
                } catch (Exception ignored) {
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("read source markdown failed", e);
        }
    }

    private List<ServerEntry> parseServerEntriesFromObjectList(List<Object> mcpObjects) {
        List<ServerEntry> out = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        for (Object obj : mcpObjects) {
            JsonNode root = objectMapper.valueToTree(obj);
            JsonNode mcpServers = root.path("mcpServers");
            if (mcpServers.isObject()) {
                mcpServers.fields().forEachRemaining(entry -> {
                    String label = safeText(entry.getKey());
                    String url = normalizeServerUrl(entry.getValue().path("url").asText(""));
                    addServerIfValid(label, url, dedup, out);
                });
                continue;
            }

            String label = safeText(root.path("serverLabel").asText(""));
            String url = normalizeServerUrl(root.path("url").asText(""));
            if (label.isBlank()) {
                label = extractServerLabelFromUrl(url);
            }
            addServerIfValid(label, url, dedup, out);
        }
        return out;
    }

    private void addServerIfValid(String label, String url, Set<String> dedup, List<ServerEntry> out) {
        if (label.isBlank() || url.isBlank()) {
            return;
        }
        String key = label + "|" + url;
        if (dedup.add(key)) {
            out.add(new ServerEntry(label, url));
        }
    }

    private String extractServerLabelFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String normalized = url.trim();
        int idx = normalized.indexOf("/proxy/");
        if (idx < 0) {
            return "";
        }
        String tail = normalized.substring(idx + "/proxy/".length());
        String[] parts = tail.split("[/?#]");
        return parts.length == 0 ? "" : safeText(parts[0]);
    }

    private String normalizeServerUrl(String rawUrl) {
        String url = safeText(rawUrl);
        if (url.isBlank()) {
            return "";
        }
        String apiKey = safeText(properties.getApiKey());
        if (apiKey.isBlank()) {
            return url;
        }
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        url = url.replace("YOUR_ZHIPU_API_KEY", encodedApiKey)
                .replace("Your_Zhipu_API_Key", encodedApiKey)
                .replace("Your Zhipu API Key", encodedApiKey)
                .replace("YOUR ZHIPU API KEY", encodedApiKey)
                .replace("YOUR_API_KEY", encodedApiKey)
                .replace("Your API Key", encodedApiKey);

        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        if (query.isBlank()) {
            return url;
        }
        String[] pairs = query.split("&");
        boolean changed = false;
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String val = eq >= 0 ? pair.substring(eq + 1) : "";
            if ("Authorization".equalsIgnoreCase(key) && isApiKeyPlaceholder(val)) {
                pairs[i] = key + "=" + encodedApiKey;
                changed = true;
            }
        }
        return changed ? (base + "?" + String.join("&", pairs)) : url;
    }

    private boolean isApiKeyPlaceholder(String value) {
        String v = safeText(value);
        if (v.isBlank()) {
            return true;
        }
        String upper = v.toUpperCase();
        return upper.contains("YOUR") && upper.contains("KEY");
    }

    private void writeMarkdown(Path outputPath, List<ResultEntry> results) {
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# MCP tools/list 导出结果\n\n");
            sb.append("- 时间: ").append(LocalDateTime.now()).append("\n");
            sb.append("- 总服务数: ").append(results.size()).append("\n\n");
            for (ResultEntry r : results) {
                sb.append("## ").append(r.serverLabel()).append("\n\n");
                sb.append("- URL: `").append(r.serverUrl()).append("`\n");
                sb.append("- 状态: ").append(r.success() ? "成功" : "失败").append("\n");
                if (!r.success()) {
                    sb.append("- 错误: ").append(r.errorMessage()).append("\n\n");
                    continue;
                }
                sb.append("- 工具数: ").append(r.tools().size()).append("\n\n");
                for (ToolItem t : r.tools()) {
                    sb.append("### ").append(t.name()).append("\n\n");
                    sb.append("- 描述: ").append(t.description().isBlank() ? "(空)" : t.description()).append("\n");
                    sb.append("- input_schema:\n\n");
                    sb.append("```json\n").append(prettyJsonOrRaw(t.inputSchema())).append("\n```\n\n");
                }
            }
            Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write output markdown failed", e);
        }
    }
    private void writeJson(Path outputPath, List<ResultEntry> results) {
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generatedAt", LocalDateTime.now().toString());
            payload.put("totalServers", results.size());
            payload.put("successCount", results.stream().filter(ResultEntry::success).count());
            payload.put("failedCount", results.stream().filter(r -> !r.success()).count());
            payload.put("results", results);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write output json failed", e);
        }
    }

    private String prettyJsonOrRaw(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return text;
        }
    }

    private <T> CompletableFuture<T> supplyAsyncNamed(String taskName, java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        TaskContext taskContext = TaskContext.capture("McpToolListOpsService", taskName);
        Runnable delegate = () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        try {
            mcpIoExecutor.execute(new NamedTaskRunnable(taskContext, delegate));
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    private void sleepQuietly(int ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mcp export interrupted while throttling", e);
        }
    }

    private Path resolvePath(String configuredPath) {
        String raw = configuredPath == null ? "" : configuredPath.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("path is blank");
        }
        Path p = Path.of(raw);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path byCwd = cwd.resolve(p).normalize();
        if (Files.exists(byCwd) || !raw.startsWith("..")) {
            return byCwd;
        }
        return cwd.resolve("..").resolve(p).normalize();
    }

    private void ensureEnabledAndApiKey() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("mcp ops disabled");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("ops.mcp.api-key is required");
        }
    }

    private String safeText(String s) {
        return s == null ? "" : s.trim();
    }

    private String safe(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private String trim(String s, int n) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= n) {
            return t;
        }
        return t.substring(0, n) + "...";
    }

    private record ServerEntry(String serverLabel, String serverUrl) {
    }

    private record ToolItem(String name, String description, String inputSchema) {
    }

    private record ResultEntry(String serverLabel, String serverUrl, boolean success, String errorMessage, List<ToolItem> tools) {
        static ResultEntry success(String serverLabel, String serverUrl, List<ToolItem> tools) {
            return new ResultEntry(serverLabel, serverUrl, true, "", tools);
        }

        static ResultEntry failure(String serverLabel, String serverUrl, String err) {
            return new ResultEntry(serverLabel, serverUrl, false, err == null ? "" : err, List.of());
        }
    }
}
