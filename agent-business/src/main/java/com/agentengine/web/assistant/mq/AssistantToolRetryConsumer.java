package com.agentengine.web.assistant.mq;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.AssistantAgentOrchestrationService;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${agent.assistant.tool-http.retry.consumer.topic:assistant_tool_retry_tasks}",
        consumerGroup = "${agent.assistant.tool-http.retry.consumer.group:assistant-tool-retry-consumer}",
        consumeThreadNumber = 8,
        consumeThreadMax = 8
)
public class AssistantToolRetryConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final AssistantStateMachineService stateMachineService;
    private final AssistantAgentOrchestrationService orchestrationService;

    @Value("${agent.assistant.tool-http.retry.max-retry:3}")
    private int maxRetry;

    @Override
    public void onMessage(String messageBody) {
        AssistantToolRetryTaskMessage message;
        try {
            message = objectMapper.readValue(messageBody, AssistantToolRetryTaskMessage.class);
        } catch (Exception e) {
            log.error("assistant tool retry consume parse failed", e);
            return;
        }
        if (message == null || blank(message.getTaskId())) {
            return;
        }
        int retryCount = message.getRetryCount() == null ? 0 : Math.max(0, message.getRetryCount());
        int allowedMaxRetry = message.getMaxRetry() == null ? maxRetry : Math.max(0, message.getMaxRetry());
        if (retryCount > allowedMaxRetry) {
            log.warn("assistant tool retry dropped because max retry exceeded. taskId={}, tool={}, retry={}, max={}",
                    message.getTaskId(), message.getToolName(), retryCount, allowedMaxRetry);
            return;
        }
        AssistantUserState state = stateMachineService.findByTaskId(message.getTaskId()).orElse(null);
        if (state == null || state.getState() != LlmAgentState.ACTIVE) {
            log.info("assistant tool retry skipped, state missing or not active. taskId={}, tool={}, state={}",
                    message.getTaskId(), message.getToolName(), state == null ? "null" : state.getState());
            return;
        }
        AssistantAgentProcessRequest request = new AssistantAgentProcessRequest();
        request.setTaskId(state.getTaskId());
        request.setUserId(state.getUserId());
        request.setTraceId(state.getTraceId());
        request.setMessage("");
        try {
            AssistantToolRetryContext.set(retryCount, allowedMaxRetry);
            orchestrationService.execute(request);
        } catch (Exception e) {
            log.error("assistant tool retry consume failed. taskId={}, tool={}, retry={}",
                    message.getTaskId(), message.getToolName(), retryCount, e);
        } finally {
            AssistantToolRetryContext.clear();
        }
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
