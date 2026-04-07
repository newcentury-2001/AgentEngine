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
     * 运维前端访问地址（WireGuard 内网）：http://10.10.10.1:18080
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentOpsApplication.class, args);
        log.info("AgentOps started");
    }
}