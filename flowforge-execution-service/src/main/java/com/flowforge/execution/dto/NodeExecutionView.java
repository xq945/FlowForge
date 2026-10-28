package com.flowforge.execution.dto;

import java.time.LocalDateTime;

public record NodeExecutionView(
        Long id,
        String nodeKey,
        String nodeType,
        String nodeName,
        String status,
        Integer retryCount,
        Integer maxRetry,
        Integer attemptNo,
        LocalDateTime nextRetryAt,
        String output,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
