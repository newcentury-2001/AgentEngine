package com.agentengine.skill.preprocess.config;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MiddlewareStartupVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareStartupVerifier.class);

    private final DataSource dataSource;
    private final RedissonClient redissonClient;

    @Value("${xxl.job.admin.addresses:}")
    private String xxlAdminAddresses;

    @Value("${rocketmq.name-server:}")
    private String rocketmqNameServer;

    @Value("${rocketmq.dashboard-url:}")
    private String rocketmqDashboardUrl;

    public MiddlewareStartupVerifier(DataSource dataSource, RedissonClient redissonClient) {
        this.dataSource = dataSource;
        this.redissonClient = redissonClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        verifyPostgresql();
        verifyRedisAndRedisson();
        verifyXxlAdmin();
        verifyRocketMqNameServer();
        verifyRocketMqDashboard();
        log.info("middleware startup verification passed");
    }

    private void verifyPostgresql() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                throw new IllegalStateException("postgresql check failed: connection invalid");
            }
            log.info("postgresql check passed");
        } catch (Exception e) {
            throw new IllegalStateException("postgresql check failed", e);
        }
    }

    private void verifyRedisAndRedisson() {
        try {
            boolean ok = redissonClient.getNodesGroup().pingAll();
            if (!ok) {
                throw new IllegalStateException("redis check failed: pingAll=false");
            }
            log.info("redis/redisson check passed");
        } catch (Exception e) {
            throw new IllegalStateException("redis/redisson check failed", e);
        }
    }

    private void verifyXxlAdmin() {
        List<String> addresses = splitAddresses(xxlAdminAddresses);
        if (addresses.isEmpty()) {
            throw new IllegalStateException("xxl admin check failed: xxl.job.admin.addresses is empty");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        List<String> errors = new ArrayList<>();
        for (String addr : addresses) {
            try {
                String normalized = addr.endsWith("/") ? addr : addr + "/";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(normalized))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code >= 200 && code < 500) {
                    log.info("xxl admin check passed: {} status={}", addr, code);
                    return;
                }
                errors.add(addr + " status=" + code);
            } catch (Exception e) {
                errors.add(addr + " error=" + e.getMessage());
            }
        }
        throw new IllegalStateException("xxl admin check failed: " + errors);
    }

    private void verifyRocketMqNameServer() {
        List<String> endpoints = splitAddresses(rocketmqNameServer);
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("rocketmq check failed: rocketmq.name-server is empty");
        }

        List<String> errors = new ArrayList<>();
        for (String endpoint : endpoints) {
            String[] pair = endpoint.split(":");
            if (pair.length != 2) {
                errors.add(endpoint + " invalid endpoint");
                continue;
            }
            String host = pair[0].trim();
            int port;
            try {
                port = Integer.parseInt(pair[1].trim());
            } catch (NumberFormatException e) {
                errors.add(endpoint + " invalid port");
                continue;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                log.info("rocketmq nameserver check passed: {}", endpoint);
                return;
            } catch (Exception e) {
                errors.add(endpoint + " error=" + e.getMessage());
            }
        }
        throw new IllegalStateException("rocketmq nameserver check failed: " + errors);
    }

    private void verifyRocketMqDashboard() {
        List<String> addresses = splitAddresses(rocketmqDashboardUrl);
        if (addresses.isEmpty()) {
            throw new IllegalStateException("rocketmq dashboard check failed: rocketmq.dashboard-url is empty");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        List<String> errors = new ArrayList<>();
        for (String addr : addresses) {
            try {
                String normalized = addr.endsWith("/") ? addr : addr + "/";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(normalized))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code >= 200 && code < 500) {
                    log.info("rocketmq dashboard check passed: {} status={}", addr, code);
                    return;
                }
                errors.add(addr + " status=" + code);
            } catch (Exception e) {
                errors.add(addr + " error=" + e.getMessage());
            }
        }
        throw new IllegalStateException("rocketmq dashboard check failed: " + errors);
    }

    private List<String> splitAddresses(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String[] parts = raw.split("[,;]");
        for (String p : parts) {
            String v = p.trim();
            if (!v.isBlank()) {
                out.add(v);
            }
        }
        return out;
    }
}
