package com.flowforge.execution.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExecutionDetail(
        Long id,
        Long workflowId,
        Long workflowVersionId,
        Integer versionNo,
        String triggerType,
        String status,
        Integer totalNodes,
        Integer finishedNodes,
        String errorCode,
        String errorMessage,
        LocalDateTime deadlineAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<NodeExecutionView> nodes
) {
}
