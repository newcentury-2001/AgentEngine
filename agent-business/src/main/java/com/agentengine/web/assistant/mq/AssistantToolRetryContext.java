package com.agentengine.web.assistant.mq;

public final class AssistantToolRetryContext {
    private static final ThreadLocal<Integer> RETRY_COUNT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> MAX_RETRY = new ThreadLocal<>();

    private AssistantToolRetryContext() {
    }

    public static void set(Integer retryCount, Integer maxRetry) {
        RETRY_COUNT.set(retryCount == null ? 0 : Math.max(0, retryCount));
        MAX_RETRY.set(maxRetry == null ? 0 : Math.max(0, maxRetry));
    }

    public static int retryCount() {
        Integer n = RETRY_COUNT.get();
        return n == null ? 0 : n;
    }

    public static int maxRetry() {
        Integer n = MAX_RETRY.get();
        return n == null ? 0 : n;
    }

    public static void clear() {
        RETRY_COUNT.remove();
        MAX_RETRY.remove();
    }
}

