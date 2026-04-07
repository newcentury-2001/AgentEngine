package com.agentcommon.concurrent;

public class ExecutorSaturatedException extends RuntimeException {

    private final String poolType;
    private final String poolName;
    private final String traceId;

    public ExecutorSaturatedException(String poolType, String poolName, String traceId, String message) {
        super(message);
        this.poolType = poolType;
        this.poolName = poolName;
        this.traceId = traceId;
    }

    public ExecutorSaturatedException(String poolType, String traceId, String message) {
        this(poolType, "-", traceId, message);
    }

    public String getPoolType() {
        return poolType;
    }

    public String getPoolName() {
        return poolName;
    }

    public String getTraceId() {
        return traceId;
    }
}
