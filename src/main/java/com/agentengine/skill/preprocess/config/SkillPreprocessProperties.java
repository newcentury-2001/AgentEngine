package com.agentengine.skill.preprocess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skill.preprocess")
public class SkillPreprocessProperties {

    private String redisToolCountKeyPattern;
    private String installLockKeyPrefix;
    private int redisScanCountHint;
    private int redisScriptBatchSize;
    private String dailyStatCron;

    public String getRedisToolCountKeyPattern() {
        return redisToolCountKeyPattern;
    }

    public void setRedisToolCountKeyPattern(String redisToolCountKeyPattern) {
        this.redisToolCountKeyPattern = redisToolCountKeyPattern;
    }

    public int getRedisScanCountHint() {
        return redisScanCountHint;
    }

    public void setRedisScanCountHint(int redisScanCountHint) {
        this.redisScanCountHint = redisScanCountHint;
    }

    public String getInstallLockKeyPrefix() {
        return installLockKeyPrefix;
    }

    public void setInstallLockKeyPrefix(String installLockKeyPrefix) {
        this.installLockKeyPrefix = installLockKeyPrefix;
    }

    public int getRedisScriptBatchSize() {
        return redisScriptBatchSize;
    }

    public void setRedisScriptBatchSize(int redisScriptBatchSize) {
        this.redisScriptBatchSize = redisScriptBatchSize;
    }

    public String getDailyStatCron() {
        return dailyStatCron;
    }

    public void setDailyStatCron(String dailyStatCron) {
        this.dailyStatCron = dailyStatCron;
    }
}
