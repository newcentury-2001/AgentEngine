package com.agentengine.skill.preprocess.aop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RequestAliveCheckAspect {

    @Before("@annotation(com.agentengine.skill.preprocess.aop.CheckRequestAlive)")
    public void checkRequestAlive() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        HttpServletResponse response = servletAttributes.getResponse();
        if (request == null) {
            throw new RequestDisconnectedException("request missing");
        }
        if (response != null && response.isCommitted()) {
            throw new RequestDisconnectedException("request disconnected");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new RequestDisconnectedException("request interrupted");
        }
    }
}
