package com.agentengine.skill.preprocess.schedule;

import com.agentengine.skill.preprocess.config.SkillPreprocessProperties;
import com.agentengine.skill.preprocess.service.ToolCallStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class ToolCallDailyStatScheduler {

    private static final Logger log = LoggerFactory.getLogger(ToolCallDailyStatScheduler.class);

    private final ToolCallStatsService statsService;
    private final SkillPreprocessProperties properties;

    public ToolCallDailyStatScheduler(ToolCallStatsService statsService, SkillPreprocessProperties properties) {
        this.statsService = statsService;
        this.properties = properties;
    }

    @Scheduled(cron = "${skill.preprocess.daily-stat-cron}")
    public void aggregateDailyStatsAt3am() {
        LocalDate today = LocalDate.now();
        Map<String, Long> todayCounts = statsService.snapshotAndResetToolCounts();
        statsService.saveDailyStats(today, todayCounts);
        Map<String, Long> recent7d = statsService.getRecent7dToolCounts();
        log.info("tool daily stats done, cron={}, todayCountSize={}, recent7dSize={}",
                properties.getDailyStatCron(), todayCounts.size(), recent7d.size());
    }
}
