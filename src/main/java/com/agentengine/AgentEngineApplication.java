package com.agentengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentEngineApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentEngineApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AgentEngineApplication.class, args);
        log.info("AgentEngine started");
        System.out.println(Runtime.getRuntime().availableProcessors());
    }
}
