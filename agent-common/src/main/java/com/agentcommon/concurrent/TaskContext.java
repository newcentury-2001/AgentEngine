package com.agentcommon.concurrent;

import org.slf4j.MDC;

public record TaskContext(
        String taskId,
        String traceId,
        String serviceName,
        String methodName
) {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TASK_ID_KEY = "taskId";
    public static final String SERVICE_NAME_KEY = "serviceName";
    public static final String METHOD_NAME_KEY = "methodName";

    public TaskContext {
        taskId = safe(taskId);
        traceId = safe(traceId);
        serviceName = safe(serviceName);
        methodName = safe(methodName);
    }

    public static TaskContext capture(String serviceName, String methodName) {
        return new TaskContext(
                MDC.get(TASK_ID_KEY),
                MDC.get(TRACE_ID_KEY),
                serviceName,
                methodName
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
