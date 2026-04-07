package com.agentengine.skill.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PostgreSQL 线程池配置属性
 */
@Data
@ConfigurationProperties(prefix = "thread-pool.pg-io")
public class PgExecutorConfigProperties {

    /**
     * 线程池核心大小（CPU 核心数 × 比例）
     */
    private double coreCpuRatio = 0.75;

    /**
     * 是否守护线程
     */
    private boolean daemon = false;

    /**
     * 拒绝策略
     */
    private String rejectPolicy = "degrade";
}
