package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("outbox_event")
@Getter
@Setter
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一事件 ID，消费端幂等依据 */
    private String eventId;

    private String eventType;

    private String aggregateType;

    private String aggregateId;

    private String payload;

    /** PENDING / SENT / FAILED */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
