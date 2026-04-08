package com.agentlog.controller;

import com.agentlog.model.pojo.LogErrorQuery;
import com.agentlog.model.vo.LogErrorPageResponse;
import com.agentlog.service.LogEventQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/log-events")
public class LogEventQueryController {

    private final LogEventQueryService queryService;

    public LogEventQueryController(LogEventQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/errors")
    public LogErrorPageResponse queryErrors(
            @RequestParam(required = false) Long startTimeMs,
            @RequestParam(required = false) Long endTimeMs,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LogErrorQuery query = new LogErrorQuery();
        query.setStartTimeMs(startTimeMs);
        query.setEndTimeMs(endTimeMs);
        query.setTraceId(traceId);
        query.setTaskId(taskId);
        query.setLevel(level);
        query.setEventType(eventType);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return queryService.queryErrors(query);
    }
}
