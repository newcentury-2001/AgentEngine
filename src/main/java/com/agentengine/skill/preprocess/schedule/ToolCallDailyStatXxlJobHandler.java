package com.agentengine.skill.preprocess.schedule;

import com.agentengine.skill.preprocess.service.ToolCallStatsService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class ToolCallDailyStatXxlJobHandler {

    private final ToolCallStatsService statsService;

    public ToolCallDailyStatXxlJobHandler(ToolCallStatsService statsService) {
        this.statsService = statsService;
    }

    @XxlJob("toolDailyStatJobHandler")
    public void aggregateDailyStats() {
        LocalDate today = LocalDate.now();
        Map<String, Long> todayCounts = statsService.snapshotAndResetToolCounts();
        statsService.saveDailyStats(today, todayCounts);
        Map<String, Long> recent7d = statsService.getRecent7dToolCounts();
        XxlJobHelper.log("tool daily stats done, todayCountSize={}, recent7dSize={}",
                todayCounts.size(), recent7d.size());
    }
}
