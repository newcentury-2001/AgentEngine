package com.agentcommon.concurrent;

import com.agentcommon.ratelimit.SlidingWindowCircuitBreaker;
import com.agentcommon.ratelimit.SlidingWindowQpsLimiter;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutorFactory {

    private ExecutorFactory() {
    }

    public enum PoolType {
        PG_IO("PostgreSQL IO thread pool"),
        REDIS_IO("Redis IO thread pool"),
        LLM_IO("LLM HTTP IO thread pool");

        private final String description;

        PoolType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public record PoolSpec(
            String bizTag,
            String role,
            String namePrefix,
            int coreSize,
            int maxSize,
            boolean daemon,
            RejectedExecutionHandler rejectedHandler,
            Boolean rateLimitEnabled,
            Integer qps,
            Boolean circuitBreakerEnabled,
            Double failureRateThreshold
    ) {
        public PoolSpec(String bizTag, String role, int coreSize, int maxSize, boolean daemon, RejectedExecutionHandler rejectedHandler) {
            this(bizTag, role, null, coreSize, maxSize, daemon, rejectedHandler, false, 0, false, 1.0d);
        }

        public PoolSpec(
                String bizTag,
                String role,
                int coreSize,
                int maxSize,
                boolean daemon,
                RejectedExecutionHandler rejectedHandler,
                boolean rateLimitEnabled,
                int qps
        ) {
            this(bizTag, role, null, coreSize, maxSize, daemon, rejectedHandler, rateLimitEnabled, qps, false, 1.0d);
        }

        public PoolSpec(
                String bizTag,
                String role,
                int coreSize,
                int maxSize,
                boolean daemon,
                RejectedExecutionHandler rejectedHandler,
                boolean rateLimitEnabled,
                int qps,
                boolean circuitBreakerEnabled,
                double failureRateThreshold
        ) {
            this(
                    bizTag,
                    role,
                    null,
                    coreSize,
                    maxSize,
                    daemon,
                    rejectedHandler,
                    rateLimitEnabled,
                    qps,
                    circuitBreakerEnabled,
                    failureRateThreshold
            );
        }

        public PoolSpec(String namePrefix, int coreSize, int maxSize, boolean daemon, RejectedExecutionHandler rejectedHandler) {
            this(null, null, namePrefix, coreSize, maxSize, daemon, rejectedHandler, false, 0, false, 1.0d);
        }

        public String resolvedNamePrefix() {
            if (namePrefix != null && !namePrefix.isBlank()) {
                return namePrefix.trim();
            }
            String biz = (bizTag == null || bizTag.isBlank()) ? "biz" : bizTag.trim();
            String r = (role == null || role.isBlank()) ? "io" : role.trim();
            return biz + "-" + r;
        }

        public boolean limiterEnabled() {
            return Boolean.TRUE.equals(rateLimitEnabled) && qps != null && qps > 0;
        }

        public boolean breakerEnabled() {
            return Boolean.TRUE.equals(circuitBreakerEnabled)
                    && failureRateThreshold != null
                    && failureRateThreshold > 0.0d
                    && failureRateThreshold < 1.0d;
        }
    }

    public static ExecutorService create(PoolType type, PoolSpec spec) {
        if (type == null) {
            throw new IllegalArgumentException("pool type is required");
        }
        if (spec == null) {
            throw new IllegalArgumentException("pool spec is required");
        }
        return newSynchronousExecutor(
                type,
                spec.resolvedNamePrefix(),
                spec.coreSize(),
                spec.maxSize(),
                spec.daemon(),
                spec.rejectedHandler(),
                spec.limiterEnabled(),
                spec.qps() == null ? 0 : spec.qps(),
                spec.breakerEnabled(),
                spec.failureRateThreshold() == null ? 1.0d : spec.failureRateThreshold()
        );
    }

    public static PoolSpec ioSpec(PoolType type, String namePrefix, int coreSize, int maxSize, boolean daemon) {
        if (type == null) {
            throw new IllegalArgumentException("pool type is required");
        }
        RejectedExecutionHandler handler = switch (type) {
            case PG_IO, REDIS_IO -> new ThreadPoolExecutor.AbortPolicy();
            case LLM_IO -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
        return new PoolSpec(namePrefix, coreSize, maxSize, daemon, handler);
    }

    public static PoolSpec pgIoSpecFromHikariMaxPoolSize(String namePrefix, int hikariMaximumPoolSize, boolean daemon) {
        int max = Math.max(1, hikariMaximumPoolSize);
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        int core = Math.max(2, (int) Math.floor(cpu * 0.75d));
        core = Math.min(core, max);
        return ioSpec(PoolType.PG_IO, namePrefix, core, max, daemon);
    }

    public static ExecutorService newSynchronousExecutor(
            String namePrefix,
            int coreSize,
            int maxSize,
            boolean daemon,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        return newSynchronousExecutor(
                PoolType.LLM_IO,
                namePrefix,
                coreSize,
                maxSize,
                daemon,
                rejectedExecutionHandler,
                false,
                0,
                false,
                1.0d
        );
    }

    public static ExecutorService newSynchronousExecutor(
            PoolType poolType,
            String namePrefix,
            int coreSize,
            int maxSize,
            boolean daemon,
            RejectedExecutionHandler rejectedExecutionHandler,
            boolean rateLimitEnabled,
            int qps,
            boolean breakerEnabled,
            double failureRateThreshold
    ) {
        ExecutorService raw = buildRawExecutor(
                namePrefix,
                coreSize,
                maxSize,
                daemon,
                rejectedExecutionHandler
        );
        SlidingWindowQpsLimiter limiter = rateLimitEnabled && qps > 0 ? new SlidingWindowQpsLimiter(qps) : null;
        SlidingWindowCircuitBreaker breaker = breakerEnabled
                ? new SlidingWindowCircuitBreaker(failureRateThreshold)
                : null;
        return new FlowControlExecutor(poolType, namePrefix, raw, limiter, breaker);
    }

    private static ExecutorService buildRawExecutor(
            String namePrefix,
            int coreSize,
            int maxSize,
            boolean daemon,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        int core = Math.max(1, coreSize);
        int max = Math.max(core, maxSize);
        return new ThreadPoolExecutor(
                core,
                max,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                namedThreadFactory(namePrefix, daemon),
                Objects.requireNonNullElseGet(rejectedExecutionHandler, ThreadPoolExecutor.AbortPolicy::new)
        );
    }

    /**
     * Backward-compatible helper. Defaults to LLM_IO semantics.
     */
    public static ExecutorService newIoExecutor(
            String namePrefix,
            int coreSize,
            int maxSize,
            boolean daemon
    ) {
        return create(
                PoolType.LLM_IO,
                ioSpec(PoolType.LLM_IO, namePrefix, coreSize, maxSize, daemon)
        );
    }

    public static ThreadFactory namedThreadFactory(String prefix, boolean daemon) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable);
            t.setName(prefix + "-pool-" + seq.getAndIncrement());
            t.setDaemon(daemon);
            return t;
        };
    }
}
