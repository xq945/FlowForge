package com.flowforge.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("notification_delivery")
@Getter
@Setter
public class NotificationDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notificationId;

    /** INBOX / MOCK_EMAIL */
    private String channel;

    /** PENDING / SENT / FAILED */
    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
