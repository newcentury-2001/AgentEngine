package com.agentengine.skill.preprocess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ZhipuHttpClientConfig {

    @Bean(name = "discoveryModelExecutor", destroyMethod = "shutdown")
    public ExecutorService discoveryModelExecutor(ZhipuProperties properties) {
        int threads = Math.max(2, properties.getDiscoveryExecutorThreads());
        return Executors.newFixedThreadPool(threads);
    }

    @Bean(name = "semanticModelExecutor", destroyMethod = "shutdown")
    public ExecutorService semanticModelExecutor(ZhipuProperties properties) {
        int threads = Math.max(2, properties.getSemanticExecutorThreads());
        return Executors.newFixedThreadPool(threads);
    }

    @Bean(name = "labelModelExecutor", destroyMethod = "shutdown")
    public ExecutorService labelModelExecutor(ZhipuProperties properties) {
        int threads = Math.max(2, properties.getLabelExecutorThreads());
        return Executors.newFixedThreadPool(threads);
    }

    @Bean(name = "embeddingModelExecutor", destroyMethod = "shutdown")
    public ExecutorService embeddingModelExecutor(ZhipuProperties properties) {
        int threads = Math.max(2, properties.getEmbeddingExecutorThreads());
        return Executors.newFixedThreadPool(threads);
    }

    @Bean
    public HttpClient zhipuHttpClient(ZhipuProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
