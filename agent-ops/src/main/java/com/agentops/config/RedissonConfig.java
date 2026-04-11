package com.agentops.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:500ms}") String timeout) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password == null || password.isBlank() ? null : password)
                .setTimeout(parseTimeoutMs(timeout));
        return Redisson.create(config);
    }

    private int parseTimeoutMs(String raw) {
        if (raw == null || raw.isBlank()) {
            return 500;
        }
        String text = raw.trim().toLowerCase();
        try {
            if (text.endsWith("ms")) {
                return Integer.parseInt(text.substring(0, text.length() - 2));
            }
            if (text.endsWith("s")) {
                return Integer.parseInt(text.substring(0, text.length() - 1)) * 1000;
            }
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return 500;
        }
    }
}

