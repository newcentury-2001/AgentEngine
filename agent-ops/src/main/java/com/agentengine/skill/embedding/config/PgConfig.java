package com.agentengine.skill.embedding.config;

import com.agentcommon.concurrent.ExecutorFactory;
import com.agentcommon.concurrent.ObservedDegradeRejectPolicy;
import com.agentengine.skill.embedding.model.pojo.PgExecutorConfigProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(PgExecutorConfigProperties.class)
public class PgConfig {

    @Bean(name = "pgIoExecutor", destroyMethod = "shutdown")
    public ExecutorService pgIoExecutor(PgExecutorConfigProperties config) {
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        int coreSize = Math.max(2, (int) Math.floor(cpu * config.getCoreCpuRatio()));

        RejectedExecutionHandler handler;
        if ("degrade".equalsIgnoreCase(config.getRejectPolicy())) {
            handler = new ObservedDegradeRejectPolicy(ExecutorFactory.PoolType.PG_IO);
        } else {
            handler = new ThreadPoolExecutor.AbortPolicy();
        }

        return ExecutorFactory.create(
                ExecutorFactory.PoolType.PG_IO,
                new ExecutorFactory.PoolSpec(
                        "embedding-pg-io",
                        coreSize,
                        coreSize,
                        config.isDaemon(),
                        handler
                )
        );
    }
}
