package com.agentengine.slot.service;

import com.agentengine.slot.config.SlotCatalogProperties;
import com.agentengine.slot.model.SlotCatalogFile;
import com.agentengine.slot.model.SlotDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SlotCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SlotCatalogService.class);

    private final SlotCatalogProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private volatile List<SlotDefinition> slots = List.of();

    public SlotCatalogService(
            SlotCatalogProperties properties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadOnStartup() {
        String path = properties.getPath();
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("slot catalog file not found: " + path);
            }
            try (InputStream in = resource.getInputStream()) {
                SlotCatalogFile file = objectMapper.readValue(in, SlotCatalogFile.class);
                List<SlotDefinition> loaded = file == null || file.slots() == null ? List.of() : file.slots();
                validate(loaded, path);
                this.slots = List.copyOf(loaded);
                log.info("slot catalog loaded: path={}, version={}, size={}",
                        path, file == null ? "" : file.version(), loaded.size());
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load slot catalog from " + path, e);
        }
    }

    public List<SlotDefinition> allSlots() {
        return slots;
    }

    private void validate(List<SlotDefinition> loaded, String path) {
        if (loaded.isEmpty()) {
            throw new IllegalStateException("slot catalog is empty: " + path);
        }
        Set<String> dedup = new HashSet<>();
        for (SlotDefinition s : loaded) {
            if (s == null || s.slotKey() == null || s.slotKey().isBlank()) {
                throw new IllegalStateException("slot key is blank in: " + path);
            }
            if (!dedup.add(s.slotKey())) {
                throw new IllegalStateException("duplicate slot key: " + s.slotKey());
            }
        }
    }
}

