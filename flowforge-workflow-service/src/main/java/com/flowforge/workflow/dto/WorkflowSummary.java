package com.flowforge.workflow.dto;

import java.time.LocalDateTime;

public record WorkflowSummary(
        Long id,
        String name,
        String description,
        String status,
        Integer latestVersionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
