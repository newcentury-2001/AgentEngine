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
    private int ioExecutorThreads = 4;
    private int requestIntervalMs = 1000;
    private String sourceMarkdownPath = "./dataset/curl.md";
    private String outputMarkdownPath = "./dataset/mcp_tools_list.md";
    private String outputJsonPath = "./dataset/mcp_tools_list_result.json";
    private String repairSourceJsonPath = "./dataset/mcp_tools_list_result_bck.json";
    private String repairOutputJsonPath = "./dataset/mcp_tools_list_result_new.json";
    private String repairOutputMarkdownPath = "./dataset/mcp_tools_list_new.md";
    private String summaryJsonPath = "./dataset/mcp_final_summary.json";
    private String cleanSystemPromptPath = "./prompts/mcp_clean_system_prompt.txt";
    private String cleanToolPromptPath = "./prompts/mcp_clean_tool_prompt.txt";
    private String cleanSkillPromptPath = "./prompts/mcp_clean_skill_prompt.txt";
    private int summaryBackupMaxFiles = 20;

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

    public int getIoExecutorThreads() {
        return ioExecutorThreads;
    }

    public void setIoExecutorThreads(int ioExecutorThreads) {
        this.ioExecutorThreads = ioExecutorThreads;
    }

    public int getRequestIntervalMs() {
        return requestIntervalMs;
    }

    public void setRequestIntervalMs(int requestIntervalMs) {
        this.requestIntervalMs = requestIntervalMs;
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

    public String getOutputJsonPath() {
        return outputJsonPath;
    }

    public void setOutputJsonPath(String outputJsonPath) {
        this.outputJsonPath = outputJsonPath;
    }

    public String getRepairSourceJsonPath() {
        return repairSourceJsonPath;
    }

    public void setRepairSourceJsonPath(String repairSourceJsonPath) {
        this.repairSourceJsonPath = repairSourceJsonPath;
    }

    public String getRepairOutputJsonPath() {
        return repairOutputJsonPath;
    }

    public void setRepairOutputJsonPath(String repairOutputJsonPath) {
        this.repairOutputJsonPath = repairOutputJsonPath;
    }

    public String getRepairOutputMarkdownPath() {
        return repairOutputMarkdownPath;
    }

    public void setRepairOutputMarkdownPath(String repairOutputMarkdownPath) {
        this.repairOutputMarkdownPath = repairOutputMarkdownPath;
    }

    public String getSummaryJsonPath() {
        return summaryJsonPath;
    }

    public void setSummaryJsonPath(String summaryJsonPath) {
        this.summaryJsonPath = summaryJsonPath;
    }

    public String getCleanSystemPromptPath() {
        return cleanSystemPromptPath;
    }

    public void setCleanSystemPromptPath(String cleanSystemPromptPath) {
        this.cleanSystemPromptPath = cleanSystemPromptPath;
    }

    public String getCleanToolPromptPath() {
        return cleanToolPromptPath;
    }

    public void setCleanToolPromptPath(String cleanToolPromptPath) {
        this.cleanToolPromptPath = cleanToolPromptPath;
    }

    public String getCleanSkillPromptPath() {
        return cleanSkillPromptPath;
    }

    public void setCleanSkillPromptPath(String cleanSkillPromptPath) {
        this.cleanSkillPromptPath = cleanSkillPromptPath;
    }

    public int getSummaryBackupMaxFiles() {
        return summaryBackupMaxFiles;
    }

    public void setSummaryBackupMaxFiles(int summaryBackupMaxFiles) {
        this.summaryBackupMaxFiles = summaryBackupMaxFiles;
    }
}
