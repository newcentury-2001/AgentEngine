package com.agentcommon.concurrent;

public class NamedTaskRunnable implements Runnable {

    private final String serviceName;
    private final String taskName;
    private final Runnable delegate;

    public NamedTaskRunnable(String serviceName, String taskName, Runnable delegate) {
        this.serviceName = serviceName == null ? "" : serviceName;
        this.taskName = taskName == null ? "" : taskName;
        this.delegate = delegate;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public void run() {
        delegate.run();
    }
}

