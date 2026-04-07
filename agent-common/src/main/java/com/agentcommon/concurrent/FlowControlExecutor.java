package com.agentcommon.concurrent;

import com.agentcommon.ratelimit.SlidingWindowCircuitBreaker;
import com.agentcommon.ratelimit.SlidingWindowQpsLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Flow-control decorator for ExecutorService.
 */
public class FlowControlExecutor implements ExecutorService {

    private static final Logger log = LoggerFactory.getLogger(FlowControlExecutor.class);
    private static final String TRACE_ID_KEY = "traceId";

    private final ExecutorFactory.PoolType poolType;
    private final String poolName;
    private final ExecutorService delegate;
    private final SlidingWindowQpsLimiter limiter;
    private final SlidingWindowCircuitBreaker circuitBreaker;

    public FlowControlExecutor(
            ExecutorFactory.PoolType poolType,
            String poolName,
            ExecutorService delegate,
            SlidingWindowQpsLimiter limiter,
            SlidingWindowCircuitBreaker circuitBreaker
    ) {
        this.poolType = Objects.requireNonNull(poolType, "poolType");
        this.poolName = (poolName == null || poolName.isBlank()) ? "-" : poolName;
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.limiter = limiter;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            boolean acquired = false;
            boolean success = false;
            SlidingWindowCircuitBreaker.AcquireDecision breakerDecision = null;
            try {
                acquireOrThrow();
                breakerDecision = acquireBreakerOrThrow();
                acquired = true;
                task.run();
                success = true;
            } finally {
                if (acquired) {
                    recordResult(breakerDecision, success);
                }
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            boolean acquired = false;
            boolean success = false;
            SlidingWindowCircuitBreaker.AcquireDecision breakerDecision = null;
            try {
                acquireOrThrow();
                breakerDecision = acquireBreakerOrThrow();
                acquired = true;
                T out = task.call();
                success = true;
                return out;
            } finally {
                if (acquired) {
                    recordResult(breakerDecision, success);
                }
            }
        };
    }

    private void acquireOrThrow() {
        if (limiter != null && !limiter.tryAcquire()) {
            String traceId = traceId();
            log.warn("qps limited, traceId={}, poolType={}, poolName={}", traceId, poolType.name(), poolName);
            throw new ExecutorSaturatedException(
                    poolType.name(),
                    poolName,
                    traceId,
                    "system busy, retry later"
            );
        }
    }

    private SlidingWindowCircuitBreaker.AcquireDecision acquireBreakerOrThrow() {
        if (circuitBreaker == null) {
            return null;
        }
        SlidingWindowCircuitBreaker.AcquireDecision decision = circuitBreaker.tryAcquire();
        if (!decision.allowed()) {
            String traceId = traceId();
            log.warn("circuit breaker open, traceId={}, poolType={}, poolName={}", traceId, poolType.name(), poolName);
            throw new ExecutorSaturatedException(
                    poolType.name(),
                    poolName,
                    traceId,
                    "service temporarily unavailable, retry later"
            );
        }
        return decision;
    }

    private void recordResult(SlidingWindowCircuitBreaker.AcquireDecision decision, boolean success) {
        if (circuitBreaker != null) {
            circuitBreaker.onComplete(decision, success);
        }
    }

    private String traceId() {
        String id = MDC.get(TRACE_ID_KEY);
        return (id == null || id.isBlank()) ? "-" : id;
    }
}
