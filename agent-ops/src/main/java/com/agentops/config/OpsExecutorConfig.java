package com.agentops.config;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ObservedDegradeRejectPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class OpsExecutorConfig {

    @Bean(name = "mcpIoExecutor", destroyMethod = "shutdown")
    public ExecutorService mcpIoExecutor(OpsMcpProperties properties) {
        int n = Math.max(2, properties.getIoExecutorThreads());
        return ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "ops-mcp",
                        "list-tools",
                        n,
                        n,
                        true,
                        new ObservedDegradeRejectPolicy(ExecutorFactory.PoolType.LLM_IO)
                )
        );
    }
}
