package com.flowforge.notification.dto;

import java.time.LocalDateTime;

public record NotificationView(
        Long id,
        String type,
        String title,
        String content,
        String bizType,
        Long bizId,
        Boolean read,
        LocalDateTime createdAt
) {
}
