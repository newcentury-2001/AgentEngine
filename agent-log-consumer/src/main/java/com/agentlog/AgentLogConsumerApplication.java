package com.agentlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class AgentLogConsumerApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentLogConsumerApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AgentLogConsumerApplication.class, args);
        String port = context.getEnvironment().getProperty("server.port", "8080");
        String host = context.getEnvironment().getProperty("app.network.wg-host", "127.0.0.1");
        log.info("Log Query UI: http://{}:{}/index.html", host, port);
        log.info("Error Query API: http://{}:{}/api/log-events/errors", host, port);
    }
}
