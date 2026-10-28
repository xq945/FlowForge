package com.flowforge.execution.dto;

import java.time.LocalDateTime;

/**
 * 单次尝试记录。{@code uncertain=true} 表示结果未知（如读超时），
 * 前端应提示「外部系统可能已执行」而不是简单的失败。
 */
public record AttemptView(
        Long id,
        Integer attemptNo,
        String status,
        String errorCode,
        String errorMessage,
        Boolean uncertain,
        String requestSummary,
        String responseSummary,
        Integer durationMs,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
