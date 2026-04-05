package com.agentops.service;

import com.agentops.config.OpsMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpToolListOpsService {

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{[\\s\\S]*?\\})\\s*```");

    private final OpsMcpProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpToolListOpsService(OpsMcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .build();
    }

    public Map<String, Object> exportToolsListToMarkdown() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("mcp ops disabled");
        }
        ensureApiKey();

        Path sourcePath = resolvePath(properties.getSourceMarkdownPath());
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("source markdown not found: " + sourcePath);
        }

        List<ServerEntry> servers = parseServerEntries(sourcePath);
        List<ResultEntry> results = new ArrayList<>();
        int ok = 0;
        int failed = 0;

        for (ServerEntry entry : servers) {
            try {
                List<ToolItem> tools = listTools(entry.serverLabel(), entry.serverUrl());
                results.add(ResultEntry.success(entry.serverLabel(), entry.serverUrl(), tools));
                ok++;
            } catch (Exception e) {
                results.add(ResultEntry.failure(entry.serverLabel(), entry.serverUrl(), e.getMessage()));
                failed++;
            }
        }

        Path outputPath = resolvePath(properties.getOutputMarkdownPath());
        writeMarkdown(outputPath, results);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "mcp tools exported");
        out.put("sourcePath", sourcePath.toString());
        out.put("outputPath", outputPath.toString());
        out.put("totalServers", servers.size());
        out.put("successCount", ok);
        out.put("failedCount", failed);
        return out;
    }

    private List<ToolItem> listTools(String serverLabel, String serverUrl) throws IOException, InterruptedException {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", safe(properties.getModel(), "glm-4-flash"));
        req.put("stream", false);
        req.put("temperature", 0.1);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", "Please call mcp_list_tools and return raw tool name, description, input_schema.");
        messages.add(user);
        req.set("messages", messages);

        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "mcp");
        ObjectNode mcp = objectMapper.createObjectNode();
        mcp.put("transport_type", "streamable-http");
        mcp.put("server_label", serverLabel);
        mcp.put("server_url", serverUrl);
        ObjectNode headers = objectMapper.createObjectNode();
        headers.put("Authorization", "Bearer " + properties.getApiKey().trim());
        mcp.set("headers", headers);
        tool.set("mcp", mcp);
        tools.add(tool);
        req.set("tools", tools);

        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofMillis(Math.max(1000, properties.getRequestTimeoutMs())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("http " + response.statusCode() + " " + trim(response.body(), 300));
        }
        JsonNode root = objectMapper.readTree(response.body());
        List<ToolItem> parsed = extractToolItems(root);
        if (parsed.isEmpty()) {
            throw new IllegalStateException("no tools parsed");
        }
        return parsed;
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
                        String url = entry.getValue().path("url").asText("").trim();
                        if (!label.isBlank() && !url.isBlank()) {
                            String key = label + "|" + url;
                            if (dedup.add(key)) {
                                out.add(new ServerEntry(label, url));
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("read source markdown failed", e);
        }
    }

    private void writeMarkdown(Path outputPath, List<ResultEntry> results) {
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# MCP tools/list 导出结果").append("\n\n");
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
                    sb.append("```json\n");
                    sb.append(prettyJsonOrRaw(t.inputSchema())).append("\n");
                    sb.append("```\n\n");
                }
            }
            Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write output markdown failed", e);
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

    private void ensureApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("ops.mcp.api-key is required");
        }
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

