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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Embedding 鏃ュ織鏂囦欢鏈嶅姟
 * 璐熻矗灏?embedding 缁撴灉鍐欏叆鏃ュ織鏂囦欢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingLogFileService {

    private final EmbeddingProperties properties;

    /**
     * 寮傛鍐欏叆鏃ュ織鏂囦欢
     *
     * @param result 鎵╁睍鐨勭粨鏋滃璞?     * @param executor 鎵ц鍣?     * @return CompletableFuture
     */
    public CompletableFuture<Void> writeLogAsync(
            EmbeddingResultExtended result,
            ExecutorService executor) {

        return CompletableFuture.runAsync(() -> {
            try {
                String logEntry = buildLogEntry(result);

                // 纭繚鏃ュ織鐩綍瀛樺湪
                Path logPath = Paths.get(properties.getLogFilePath());
                Files.createDirectories(logPath.getParent());

                // 鍐欏叆鏃ュ織鏂囦欢
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

    /**
     * 鏋勫缓鏃ュ織鏉＄洰
     */
    private String buildLogEntry(EmbeddingResultExtended result) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String itemType = result.getItemType() != null ? result.getItemType() : "item";

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
                result.getFailedItems().isEmpty() ? "None" :
                        result.getFailedItems().stream()
                                .sorted()
                                .collect(java.util.stream.Collectors.joining("\n", "\n", "\n")),
                result.getDatabaseFailedItems().isEmpty() ? "None" :
                        result.getDatabaseFailedItems().stream()
                                .sorted()
                                .collect(java.util.stream.Collectors.joining("\n", "\n", "\n"))
        );
    }

    /**
     * 棣栧瓧姣嶅ぇ鍐?     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

