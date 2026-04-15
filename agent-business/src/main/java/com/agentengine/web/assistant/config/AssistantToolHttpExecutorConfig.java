package com.agentengine.web.assistant.config;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ObservedDegradeRejectPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class AssistantToolHttpExecutorConfig {

    @Bean(name = "assistantToolHttpExecutor", destroyMethod = "shutdown")
    public ExecutorService assistantToolHttpExecutor(
            @Value("${agent.assistant.tool-http.executor.size:16}") int size,
            @Value("${agent.assistant.tool-http.flow.rate-limit-enabled:true}") boolean rateLimitEnabled,
            @Value("${agent.assistant.tool-http.flow.qps:120}") int qps,
            @Value("${agent.assistant.tool-http.flow.circuit-breaker-enabled:true}") boolean circuitBreakerEnabled,
            @Value("${agent.assistant.tool-http.flow.failure-rate-threshold:0.5}") double failureRateThreshold) {
        int n = Math.max(2, size);
        return ExecutorFactory.create(
                ExecutorFactory.PoolType.LLM_IO,
                new ExecutorFactory.PoolSpec(
                        "assistant-tool",
                        "thirdparty-http",
                        n,
                        n,
                        true,
                        new ObservedDegradeRejectPolicy(ExecutorFactory.PoolType.LLM_IO),
                        rateLimitEnabled,
                        Math.max(1, qps),
                        circuitBreakerEnabled,
                        failureRateThreshold
                )
        );
    }
}

