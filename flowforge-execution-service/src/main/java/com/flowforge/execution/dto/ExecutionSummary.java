package com.flowforge.execution.dto;

import java.time.LocalDateTime;

public record ExecutionSummary(
        Long id,
        Long workflowId,
        Integer versionNo,
        String triggerType,
        String status,
        Integer totalNodes,
        Integer finishedNodes,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
