package com.agentops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentOpsApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentOpsApplication.class);

    /**
     * Agent Ops 启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentOpsApplication.class, args);
        log.info("AgentOps started");
    }
}
