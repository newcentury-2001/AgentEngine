package com.agentlog.service;

import com.agentcommon.ratelimit.SlidingWindowQpsLimiter;
import com.agentlog.config.DbPersistExecutorProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PartitionDbExecutorRouter {

    private final ConcurrentHashMap<Integer, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SlidingWindowQpsLimiter> limiters = new ConcurrentHashMap<>();
    private final DbPersistExecutorProperties properties;

    public PartitionDbExecutorRouter(DbPersistExecutorProperties properties) {
        this.properties = properties;
    }

    public CompletableFuture<Void> submit(int partition, Runnable task) {
        Objects.requireNonNull(task, "task");
        ThreadPoolExecutor executor = executors.computeIfAbsent(partition, this::newSingleThreadExecutor);
        SlidingWindowQpsLimiter limiter = limiters.computeIfAbsent(partition, this::newLimiter);

        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (!limiter.tryAcquire()) {
            // Limiter denies this task, fallback to reject-policy semantics (caller runs).
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(wrapped, executor);
            return future;
        }

        try {
            executor.execute(wrapped);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    @PreDestroy
    public void shutdown() {
        List<ExecutorService> all = new ArrayList<>(executors.values());
        for (ExecutorService executor : all) {
            executor.shutdown();
        }
        for (ExecutorService executor : all) {
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ThreadPoolExecutor newSingleThreadExecutor(int partition) {
        int queueSize = Math.max(50, properties.getQueueSize());
        ThreadFactory tf = new NamedThreadFactory(properties.getThreadNamePrefix() + "-p" + partition);
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                tf,
                // caller-runs: slow down polling, let backlog stay in Kafka.
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private SlidingWindowQpsLimiter newLimiter(int partition) {
        int qps = Math.max(1, properties.getQpsLimitPerPartition());
        return new SlidingWindowQpsLimiter(qps);
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger idx = new AtomicInteger(0);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + idx.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
