package com.agentengine.web.assistant.model;

public enum LlmAgentState {
    INIT,
    INTENT_RECOGNITION,
    TOOL_EXECUTION,
    SLOT_CLARIFICATION,
    FINAL_ANSWER,
    FAILED;

    public boolean isTerminal() {
        return this == FINAL_ANSWER || this == FAILED;
    }
}
