package com.agentcommon.http.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.http.llm")
public class LlmHttpClientPoolProperties {

    private int defaultConnectTimeoutMs = 3000;
    private int defaultExecutorThreads = 8;
    private String defaultModel = "glm-4-flash";
    private Map<String, Pool> pools = new LinkedHashMap<>();

    public LlmHttpClientPoolProperties() {
        pools.put("glm-4-flash", new Pool(3000, 12));
        pools.put("glm-4-air", new Pool(3000, 10));
        pools.put("glm-4-plus", new Pool(3000, 16));
        pools.put("glm-4.5", new Pool(3000, 20));
        pools.put("embedding-3", new Pool(3000, 8));
    }

    public int getDefaultConnectTimeoutMs() {
        return defaultConnectTimeoutMs;
    }

    public void setDefaultConnectTimeoutMs(int defaultConnectTimeoutMs) {
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
    }

    public int getDefaultExecutorThreads() {
        return defaultExecutorThreads;
    }

    public void setDefaultExecutorThreads(int defaultExecutorThreads) {
        this.defaultExecutorThreads = defaultExecutorThreads;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Map<String, Pool> getPools() {
        return pools;
    }

    public void setPools(Map<String, Pool> pools) {
        this.pools = pools;
    }

    public static class Pool {
        private int connectTimeoutMs = 3000;
        private int executorThreads = 8;

        public Pool() {
        }

        public Pool(int connectTimeoutMs, int executorThreads) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.executorThreads = executorThreads;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getExecutorThreads() {
            return executorThreads;
        }

        public void setExecutorThreads(int executorThreads) {
            this.executorThreads = executorThreads;
        }
    }
}
