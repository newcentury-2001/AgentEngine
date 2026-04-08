package com.agentcommon.log;

import com.agentcommon.log.model.LogEvent;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;

@Aspect
public class ExceptionKafkaLogAspect {

    private static final int MAX_STACK_LEN = 8000;

    private final LogKafkaProducer logKafkaProducer;

    public ExceptionKafkaLogAspect(LogKafkaProducer logKafkaProducer) {
        this.logKafkaProducer = logKafkaProducer;
    }

    @AfterThrowing(
            pointcut = "("
                    + "within(@org.springframework.stereotype.Service *) || "
                    + "within(@org.springframework.web.bind.annotation.RestController *) || "
                    + "within(@org.springframework.stereotype.Component *)"
                    + ") && execution(public * *(..)) && !within(com.agentcommon.log..*)",
            throwing = "ex"
    )
    public void onException(JoinPoint joinPoint, Throwable ex) {
        if (ex == null) {
            return;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getName();

        LogEvent event = new LogEvent();
        event.setLevel("ERROR");
        event.setEventType("UNCAUGHT_EXCEPTION");
        event.setTraceId(MDC.get("traceId"));
        event.setTaskId(MDC.get("taskId"));
        event.setServiceName(className);
        event.setMethodName(methodName);
        event.setLoggerName(className);
        event.setThreadName(Thread.currentThread().getName());
        event.setMessage(buildMessage(className, methodName, ex));
        event.setExceptionClass(ex.getClass().getName());
        event.setStackTrace(trim(stackTraceOf(ex), MAX_STACK_LEN));

        logKafkaProducer.send(event);
    }

    private String buildMessage(String className, String methodName, Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return className + "#" + methodName + " failed";
        }
        return className + "#" + methodName + " failed: " + msg;
    }

    private String stackTraceOf(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append('\n');
        for (StackTraceElement one : ex.getStackTrace()) {
            sb.append("    at ").append(one).append('\n');
            if (sb.length() > MAX_STACK_LEN) {
                break;
            }
        }
        return sb.toString();
    }

    private String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
