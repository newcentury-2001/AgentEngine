package com.agentlog.model.vo;

import java.util.List;

public class LogErrorPageResponse {

    private int page;
    private int size;
    private long total;
    private List<LogErrorRecord> items;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<LogErrorRecord> getItems() {
        return items;
    }

    public void setItems(List<LogErrorRecord> items) {
        this.items = items;
    }
}
