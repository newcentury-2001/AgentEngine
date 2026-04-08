package com.agentcommon.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ZhipuHttpProtocol {

    public static final String EMBEDDINGS_PATH = "/embeddings";
    public static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private ZhipuHttpProtocol() {
    }

    public static String endpoint(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    public static String bearerValue(String apiKey) {
        String key = apiKey == null ? "" : apiKey.trim();
        return "Bearer " + key;
    }

    public static Map<String, String> jsonHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", bearerValue(apiKey));
        return headers;
    }

    public static HttpRequest.Builder authorizedJsonPostBuilder(String baseUrl, String path, String apiKey) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint(baseUrl, path)))
                .header("Content-Type", "application/json")
                .header("Authorization", bearerValue(apiKey));
    }
}
