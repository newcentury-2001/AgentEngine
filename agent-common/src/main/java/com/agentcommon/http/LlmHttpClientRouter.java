package com.agentcommon.http;

import com.agentcommon.http.config.LlmHttpClientPoolProperties;
import org.springframework.beans.factory.DisposableBean;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LlmHttpClientRouter implements DisposableBean {

    private final String defaultModel;
    private final HttpClient defaultClient;
    private final Map<String, HttpClient> clientsByModel = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorsByModel = new ConcurrentHashMap<>();

    public LlmHttpClientRouter(LlmHttpClientPoolProperties properties) {
        this.defaultModel = normalize(properties.getDefaultModel());

        if (properties.getPools() != null) {
            for (Map.Entry<String, LlmHttpClientPoolProperties.Pool> entry : properties.getPools().entrySet()) {
                String model = normalize(entry.getKey());
                LlmHttpClientPoolProperties.Pool pool = entry.getValue();
                int timeoutMs = pool == null ? properties.getDefaultConnectTimeoutMs() : pool.getConnectTimeoutMs();
                int threads = pool == null ? properties.getDefaultExecutorThreads() : pool.getExecutorThreads();

                ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, threads));
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                        .executor(executor)
                        .build();

                executorsByModel.put(model, executor);
                clientsByModel.put(model, client);
            }
        }

        HttpClient fallback = clientsByModel.get(defaultModel);
        if (fallback == null) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, properties.getDefaultExecutorThreads()));
            fallback = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofMillis(Math.max(100, properties.getDefaultConnectTimeoutMs())))
                    .executor(executor)
                    .build();
            executorsByModel.put(defaultModel, executor);
            clientsByModel.put(defaultModel, fallback);
        }
        this.defaultClient = fallback;
    }

    public HttpClient getClient(String model) {
        String normalized = normalize(model);
        if (normalized.isEmpty()) {
            return defaultClient;
        }
        return clientsByModel.getOrDefault(normalized, defaultClient);
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    private String normalize(String model) {
        return model == null ? "" : model.trim().toLowerCase();
    }

    @Override
    public void destroy() {
        for (ExecutorService executor : executorsByModel.values()) {
            executor.shutdown();
        }
    }
}
