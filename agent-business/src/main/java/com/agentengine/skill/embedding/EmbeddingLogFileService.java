package com.agentengine.skill.embedding;

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
 * Embedding 日志文件服务
 * 负责将 embedding 结果写入日志文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingLogFileService {

    private final EmbeddingProperties properties;

    /**
     * 异步写入日志文件
     *
     * @param result 扩展的结果对象
     * @param executor 执行器
     * @return CompletableFuture
     */
    public CompletableFuture<Void> writeLogAsync(
            EmbeddingResultExtended result,
            ExecutorService executor) {

        return CompletableFuture.runAsync(() -> {
            try {
                String logEntry = buildLogEntry(result);

                // 确保日志目录存在
                Path logPath = Paths.get(properties.getLogFilePath());
                Files.createDirectories(logPath.getParent());

                // 写入日志文件
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
     * 构建日志条目
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
     * 首字母大写
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
