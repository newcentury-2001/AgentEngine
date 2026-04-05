package com.agentengine.slot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "slot.catalog")
public class SlotCatalogProperties {

    /**
     * 支持 classpath: 或绝对路径。
     */
    private String path = "classpath:slot/multiwoz_slots_100.json";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

