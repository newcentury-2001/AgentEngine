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
    private int ioExecutorThreads;
    private int cpuExecutorThreads;
    private int discoveryMaxInflight;
    private int semanticMaxInflight;
    private int labelMaxInflight;
    private int embeddingMaxInflight;
    private int modelAcquireTimeoutMs;
    private int breakerFailureThreshold;
    private int breakerOpenMs;

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

    public int getIoExecutorThreads() {
        return ioExecutorThreads;
    }

    public void setIoExecutorThreads(int ioExecutorThreads) {
        this.ioExecutorThreads = ioExecutorThreads;
    }

    public int getCpuExecutorThreads() {
        return cpuExecutorThreads;
    }

    public void setCpuExecutorThreads(int cpuExecutorThreads) {
        this.cpuExecutorThreads = cpuExecutorThreads;
    }

    public int getDiscoveryMaxInflight() {
        return discoveryMaxInflight;
    }

    public void setDiscoveryMaxInflight(int discoveryMaxInflight) {
        this.discoveryMaxInflight = discoveryMaxInflight;
    }

    public int getSemanticMaxInflight() {
        return semanticMaxInflight;
    }

    public void setSemanticMaxInflight(int semanticMaxInflight) {
        this.semanticMaxInflight = semanticMaxInflight;
    }

    public int getLabelMaxInflight() {
        return labelMaxInflight;
    }

    public void setLabelMaxInflight(int labelMaxInflight) {
        this.labelMaxInflight = labelMaxInflight;
    }

    public int getEmbeddingMaxInflight() {
        return embeddingMaxInflight;
    }

    public void setEmbeddingMaxInflight(int embeddingMaxInflight) {
        this.embeddingMaxInflight = embeddingMaxInflight;
    }

    public int getModelAcquireTimeoutMs() {
        return modelAcquireTimeoutMs;
    }

    public void setModelAcquireTimeoutMs(int modelAcquireTimeoutMs) {
        this.modelAcquireTimeoutMs = modelAcquireTimeoutMs;
    }

    public int getBreakerFailureThreshold() {
        return breakerFailureThreshold;
    }

    public void setBreakerFailureThreshold(int breakerFailureThreshold) {
        this.breakerFailureThreshold = breakerFailureThreshold;
    }

    public int getBreakerOpenMs() {
        return breakerOpenMs;
    }

    public void setBreakerOpenMs(int breakerOpenMs) {
        this.breakerOpenMs = breakerOpenMs;
    }
}
