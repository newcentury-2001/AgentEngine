package com.agentengine.slot.model;

import java.util.List;

public record SlotCatalogFile(
        String version,
        List<SlotDefinition> slots
) {
}

