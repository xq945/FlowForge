package com.flowforge.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("notification_record")
@Getter
@Setter
public class NotificationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    /** 来源消息 eventId，唯一键——重复消息不会产生第二条通知 */
    private String sourceEventId;

    private Integer readFlag;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
