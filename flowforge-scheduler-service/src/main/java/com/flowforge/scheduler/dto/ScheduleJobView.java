package com.flowforge.scheduler.dto;

import java.time.LocalDateTime;

public record ScheduleJobView(
        Long id,
        Long workflowId,
        String name,
        String cronExpr,
        String timezone,
        Boolean enabled,
        LocalDateTime nextFireTime,
        LocalDateTime lastFireTime,
        LocalDateTime createdAt
) {
}
