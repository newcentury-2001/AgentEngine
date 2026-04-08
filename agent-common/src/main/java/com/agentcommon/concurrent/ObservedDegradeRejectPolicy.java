package com.agentcommon.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class ObservedDegradeRejectPolicy implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(ObservedDegradeRejectPolicy.class);
    private static final String TRACE_ID_KEY = TaskContext.TRACE_ID_KEY;
    private static final String TASK_ID_KEY = TaskContext.TASK_ID_KEY;

    private final ExecutorFactory.PoolType poolType;

    public ObservedDegradeRejectPolicy(ExecutorFactory.PoolType poolType) {
        this.poolType = poolType;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        String traceId = traceId();
        String taskId = taskId();
        String serviceName = "-";
        String taskName = "-";
        String serviceMethod = "-";
        if (r instanceof NamedTaskRunnable namedTask) {
            serviceName = safe(namedTask.getServiceName());
            taskName = safe(namedTask.getMethodName());
            serviceMethod = serviceName + "#" + taskName;
            if ("-".equals(taskId)) {
                taskId = safe(namedTask.getTaskId());
            }
        }
        log.error(
                "executor rejected, traceId={}, taskId={}, activeCount={}, poolSize={}, peakSize={}, poolType={}, isShutdown={}, serviceMethod={}, serviceName={}, taskName={}",
                traceId,
                taskId,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getLargestPoolSize(),
                poolType.name(),
                executor.isShutdown(),
                serviceMethod,
                serviceName,
                taskName
        );
        throw new ExecutorSaturatedException(
                poolType.name(),
                traceId,
                "系统繁忙，请稍后重试"
        );
    }

    private String traceId() {
        String id = MDC.get(TRACE_ID_KEY);
        return (id == null || id.isBlank()) ? "-" : id;
    }

    private String taskId() {
        String id = MDC.get(TASK_ID_KEY);
        return (id == null || id.isBlank()) ? "-" : id;
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
