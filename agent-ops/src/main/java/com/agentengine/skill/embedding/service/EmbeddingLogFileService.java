package com.agentengine.skill.embedding.service;

import com.agentengine.skill.embedding.model.pojo.EmbeddingProperties;
import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingLogFileService {

    private final EmbeddingProperties properties;

    public CompletableFuture<Void> writeLogAsync(EmbeddingResultExtended result, ExecutorService executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                String logEntry = buildLogEntry(result);
                Path logPath = Paths.get(properties.getLogFilePath());
                Path parent = logPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        logPath,
                        logEntry,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
                log.info("Embedding results written to: {}", properties.getLogFilePath());
            } catch (Exception e) {
                log.error("Failed to write embedding results to log file", e);
            }
        }, executor);
    }

    private String buildLogEntry(EmbeddingResultExtended result) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String itemType = result.getItemType() != null ? result.getItemType() : "item";
        List<String> failedItems = safeList(result.getFailedItems());
        List<String> dbFailedItems = safeList(result.getDatabaseFailedItems());

        return String.format(
                """
                ======================================
                %s Embedding Generation Report
                ======================================
                Timestamp: %s
                Total Items: %d
                Embedding Success: %d
                Embedding Failure: %d
                Database Success: %d
                Database Failure: %d
                Embedding Time: %dms
                Database Time: %dms
                Total Time: %dms
                ======================================
                Failed Items:
                %s
                Database Failed Items:
                %s
                ======================================
                """,
                capitalizeFirst(itemType),
                timestamp,
                result.getTotalItems(),
                result.getEmbeddingSuccessCount(),
                result.getEmbeddingFailureCount(),
                result.getDatabaseSuccessCount(),
                result.getDatabaseFailureCount(),
                result.getEmbeddingTimeMs(),
                result.getDatabaseTimeMs(),
                result.getTotalTimeMs(),
                failedItems.isEmpty() ? "None" : failedItems.stream().sorted().collect(Collectors.joining("\n", "\n", "\n")),
                dbFailedItems.isEmpty() ? "None" : dbFailedItems.stream().sorted().collect(Collectors.joining("\n", "\n", "\n"))
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
