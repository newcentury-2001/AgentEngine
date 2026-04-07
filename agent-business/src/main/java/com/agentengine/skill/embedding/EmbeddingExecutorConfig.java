package com.agentengine.skill.embedding;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ObservedDegradeRejectPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Embedding 执行器配置
 */
@Configuration
public class EmbeddingExecutorConfig {

    /**
     * Embedding 线程池
     */
    @Bean(name = "embeddingExecutor", destroyMethod = "shutdown")
    public com.agentcommon.concurrent.FlowControlExecutor embeddingExecutor(
            EmbeddingProperties properties) {
        return (com.agentcommon.concurrent.FlowControlExecutor) ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "embedding",
                        "io",
                        "embedding-io",
                        properties.getThreadPoolCoreSize(),
                        properties.getThreadPoolMaxSize(),
                        true,
                        new ObservedDegradeRejectPolicy(ExecutorFactory.PoolType.LLM_IO),
                        true,
                        properties.getQpsLimit(),
                        true,
                        properties.getFailureRateThreshold()
                )
        );
    }

    /**
     * HTTP 客户端
     */
    @Bean(name = "embeddingHttpClient")
    public HttpClient embeddingHttpClient(EmbeddingProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }
}
