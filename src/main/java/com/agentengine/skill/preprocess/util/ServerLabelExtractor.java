package com.agentengine.skill.preprocess.util;

public final class ServerLabelExtractor {

    private ServerLabelExtractor() {
    }

    public static String fromServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "";
        }
        String url = serverUrl.trim();
        int i = url.indexOf("/proxy/");
        if (i >= 0) {
            String tail = url.substring(i + "/proxy/".length());
            int slash = tail.indexOf('/');
            if (slash > 0) {
                return tail.substring(0, slash);
            }
            return tail;
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1).replace("mcp", "").replaceAll("[-_]+$", "");
        }
        return "unknown-skill";
    }
}

