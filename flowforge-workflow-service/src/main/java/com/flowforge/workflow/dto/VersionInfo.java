package com.flowforge.workflow.dto;

import java.time.LocalDateTime;

public record VersionInfo(
        Long id,
        Integer versionNo,
        String status,
        String contentHash,
        LocalDateTime publishedAt
) {
}
