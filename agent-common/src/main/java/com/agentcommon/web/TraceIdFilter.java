package com.agentcommon.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TASK_ID_KEY = "taskId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TASK_ID_HEADER = "X-Task-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String currentTraceId = MDC.get(TRACE_ID_KEY);
        String currentTaskId = MDC.get(TASK_ID_KEY);
        String traceId = resolveTraceId(request);
        String taskId = resolveTaskId(request);
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(TASK_ID_KEY, taskId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(TASK_ID_HEADER, taskId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (currentTraceId == null || currentTraceId.isBlank()) {
                MDC.remove(TRACE_ID_KEY);
            } else {
                MDC.put(TRACE_ID_KEY, currentTraceId);
            }
            if (currentTaskId == null || currentTaskId.isBlank()) {
                MDC.remove(TASK_ID_KEY);
            } else {
                MDC.put(TASK_ID_KEY, currentTaskId);
            }
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String candidate = firstNonBlank(
                request.getHeader(TRACE_ID_HEADER),
                request.getHeader("traceId"),
                request.getHeader("X-Request-Id"),
                request.getParameter("traceId")
        );
        if (candidate != null) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveTaskId(HttpServletRequest request) {
        String candidate = firstNonBlank(
                request.getHeader(TASK_ID_HEADER),
                request.getHeader("taskId"),
                request.getParameter("taskId")
        );
        if (candidate != null) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    return trimmed;
                }
            }
        }
        return null;
    }
}
