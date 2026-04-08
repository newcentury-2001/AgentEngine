package com.agentcommon.concurrent;

public class NamedTaskRunnable implements Runnable {

    private final TaskContext taskContext;
    private final Runnable delegate;

    public NamedTaskRunnable(TaskContext taskContext, Runnable delegate) {
        this.taskContext = taskContext == null ? new TaskContext("", "", "", "") : taskContext;
        this.delegate = delegate;
    }

    public String getServiceName() {
        return taskContext.serviceName();
    }

    public String getTaskName() {
        return taskContext.methodName();
    }

    public String getMethodName() {
        return taskContext.methodName();
    }

    public String getTaskId() {
        return taskContext.taskId();
    }

    public String getTraceId() {
        return taskContext.traceId();
    }

    public TaskContext getTaskContext() {
        return taskContext;
    }

    @Override
    public void run() {
        String oldTraceId = org.slf4j.MDC.get(TaskContext.TRACE_ID_KEY);
        String oldTaskId = org.slf4j.MDC.get(TaskContext.TASK_ID_KEY);
        String oldServiceName = org.slf4j.MDC.get(TaskContext.SERVICE_NAME_KEY);
        String oldMethodName = org.slf4j.MDC.get(TaskContext.METHOD_NAME_KEY);

        putOrRemove(TaskContext.TRACE_ID_KEY, taskContext.traceId());
        putOrRemove(TaskContext.TASK_ID_KEY, taskContext.taskId());
        putOrRemove(TaskContext.SERVICE_NAME_KEY, taskContext.serviceName());
        putOrRemove(TaskContext.METHOD_NAME_KEY, taskContext.methodName());

        try {
            delegate.run();
        } finally {
            restore(TaskContext.TRACE_ID_KEY, oldTraceId);
            restore(TaskContext.TASK_ID_KEY, oldTaskId);
            restore(TaskContext.SERVICE_NAME_KEY, oldServiceName);
            restore(TaskContext.METHOD_NAME_KEY, oldMethodName);
        }
    }

    private void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            org.slf4j.MDC.remove(key);
            return;
        }
        org.slf4j.MDC.put(key, value);
    }

    private void restore(String key, String oldValue) {
        if (oldValue == null || oldValue.isBlank()) {
            org.slf4j.MDC.remove(key);
            return;
        }
        org.slf4j.MDC.put(key, oldValue);
    }
}
