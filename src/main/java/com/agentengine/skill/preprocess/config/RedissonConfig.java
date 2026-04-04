package com.agentengine.skill.preprocess.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        String host = redisProperties.getHost() == null || redisProperties.getHost().isBlank()
                ? "127.0.0.1"
                : redisProperties.getHost();
        int port = redisProperties.getPort() <= 0 ? 6379 : redisProperties.getPort();

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(blankToNull(redisProperties.getPassword()));
        return Redisson.create(config);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
