package com.flowforge.workflow.dto;

import java.time.LocalDateTime;

public record PublishResult(
        Long versionId,
        Integer versionNo,
        String contentHash,
        LocalDateTime publishedAt
) {
}
