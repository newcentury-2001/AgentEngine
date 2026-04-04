package com.agentengine.skill.preprocess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ZhipuHttpClientConfig {

    @Bean(name = "ioModelExecutor", destroyMethod = "shutdown")
    public ExecutorService ioModelExecutor(ZhipuProperties properties) {
        int core = Math.max(2, properties.getIoExecutorThreads());
        return newModelExecutor("io-model", core);
    }

    @Bean(name = "cpuComputeExecutor", destroyMethod = "shutdown")
    public ExecutorService cpuComputeExecutor(ZhipuProperties properties) {
        int core = Math.max(2, properties.getCpuExecutorThreads());
        return newModelExecutor("cpu-compute", core);
    }

    @Bean
    public HttpClient zhipuHttpClient(ZhipuProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private ExecutorService newModelExecutor(String namePrefix, int coreSize) {
        int maxSize = Math.max(coreSize, (int) Math.ceil(coreSize * 1.5d));
        BlockingQueue<Runnable> zeroQueue = new SynchronousQueue<>();
        ThreadFactory threadFactory = namedThreadFactory(namePrefix);
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                zeroQueue,
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable);
            t.setName(prefix + "-pool-" + seq.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
    }
}
