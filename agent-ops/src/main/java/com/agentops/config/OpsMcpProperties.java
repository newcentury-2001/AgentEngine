package com.agentops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ops.mcp")
public class OpsMcpProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String model = "glm-4-flash";
    private int connectTimeoutMs = 3000;
    private int requestTimeoutMs = 30000;
    private String sourceMarkdownPath = "../dataset/curl.md";
    private String outputMarkdownPath = "../dataset/mcp_tools_list.md";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public String getSourceMarkdownPath() {
        return sourceMarkdownPath;
    }

    public void setSourceMarkdownPath(String sourceMarkdownPath) {
        this.sourceMarkdownPath = sourceMarkdownPath;
    }

    public String getOutputMarkdownPath() {
        return outputMarkdownPath;
    }

    public void setOutputMarkdownPath(String outputMarkdownPath) {
        this.outputMarkdownPath = outputMarkdownPath;
    }
}

