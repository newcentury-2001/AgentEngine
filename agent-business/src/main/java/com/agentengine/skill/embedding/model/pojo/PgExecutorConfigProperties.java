package com.agentengine.skill.embedding.model.pojo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PostgreSQL 绾跨▼姹犻厤缃睘鎬? */
@Data
@ConfigurationProperties(prefix = "thread-pool.pg-io")
public class PgExecutorConfigProperties {

    /**
     * 绾跨▼姹犳牳蹇冨ぇ灏忥紙CPU 鏍稿績鏁?脳 姣斾緥锛?     */
    private double coreCpuRatio = 0.75;

    /**
     * 鏄惁瀹堟姢绾跨▼
     */
    private boolean daemon = false;

    /**
     * 鎷掔粷绛栫暐
     */
    private String rejectPolicy = "degrade";
}


