package com.agentengine.skill.embedding.config;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ObservedDegradeRejectPolicy;
import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;

@Configuration
public class EmbeddingExecutorConfig {

    @Bean(name = "embeddingExecutor", destroyMethod = "shutdown")
    public ExecutorService embeddingExecutor(EmbeddingProperties properties) {
        RejectedExecutionHandler handler =
                new ObservedDegradeRejectPolicy(ExecutorFactory.PoolType.LLM_IO);
        return ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "embedding-io",
                        properties.getThreadPoolCoreSize(),
                        properties.getThreadPoolMaxSize(),
                        true,
                        handler
                )
        );
    }

}
