package com.agentengine.skill.preprocess.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerLabelExtractor {

    private static final Pattern PROXY_ENDPOINT_PATTERN =
            Pattern.compile("/proxy/([^/]+)/(mcp|sse)(?:[/?#]|$)", Pattern.CASE_INSENSITIVE);

    private ServerLabelExtractor() {
    }

    public static String fromServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "";
        }
        String url = serverUrl.trim();
        Matcher matcher = PROXY_ENDPOINT_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        int i = url.indexOf("/proxy/");
        if (i >= 0) {
            String tail = url.substring(i + "/proxy/".length());
            int slash = tail.indexOf('/');
            if (slash > 0) {
                return tail.substring(0, slash);
            }
        }

        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1).replaceAll("[-_]+$", "");
        }
        return "unknown-skill";
    }
}
