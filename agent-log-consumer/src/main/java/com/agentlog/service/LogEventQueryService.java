package com.agentlog.service;

import com.agentlog.model.pojo.LogErrorQuery;
import com.agentlog.model.vo.LogErrorPageResponse;
import com.agentlog.repository.LogEventRepository;
import org.springframework.stereotype.Service;

@Service
public class LogEventQueryService {

    private final LogEventRepository repository;

    public LogEventQueryService(LogEventRepository repository) {
        this.repository = repository;
    }

    public LogErrorPageResponse queryErrors(LogErrorQuery query) {
        LogErrorPageResponse response = new LogErrorPageResponse();
        response.setPage(Math.max(1, query.getPage()));
        response.setSize(Math.max(1, Math.min(500, query.getSize())));
        response.setTotal(repository.countErrorLogs(query));
        response.setItems(repository.queryErrorLogs(query));
        return response;
    }
}
