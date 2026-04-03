package com.agentengine.skill.preprocess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zhipu")
public class ZhipuProperties {

    private String apiKey;
    private String baseUrl;
    private String discoveryModel;
    private String semanticModel;
    private String labelModel;
    private String embeddingModel;
    private int connectTimeoutMs;
    private int requestTimeoutMs;
    private int discoveryExecutorThreads;
    private int semanticExecutorThreads;
    private int labelExecutorThreads;
    private int embeddingExecutorThreads;

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

    public String getDiscoveryModel() {
        return discoveryModel;
    }

    public void setDiscoveryModel(String discoveryModel) {
        this.discoveryModel = discoveryModel;
    }

    public String getSemanticModel() {
        return semanticModel;
    }

    public void setSemanticModel(String semanticModel) {
        this.semanticModel = semanticModel;
    }

    public String getLabelModel() {
        return labelModel;
    }

    public void setLabelModel(String labelModel) {
        this.labelModel = labelModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getDiscoveryExecutorThreads() {
        return discoveryExecutorThreads;
    }

    public void setDiscoveryExecutorThreads(int discoveryExecutorThreads) {
        this.discoveryExecutorThreads = discoveryExecutorThreads;
    }

    public int getSemanticExecutorThreads() {
        return semanticExecutorThreads;
    }

    public void setSemanticExecutorThreads(int semanticExecutorThreads) {
        this.semanticExecutorThreads = semanticExecutorThreads;
    }

    public int getLabelExecutorThreads() {
        return labelExecutorThreads;
    }

    public void setLabelExecutorThreads(int labelExecutorThreads) {
        this.labelExecutorThreads = labelExecutorThreads;
    }

    public int getEmbeddingExecutorThreads() {
        return embeddingExecutorThreads;
    }

    public void setEmbeddingExecutorThreads(int embeddingExecutorThreads) {
        this.embeddingExecutorThreads = embeddingExecutorThreads;
    }
}
