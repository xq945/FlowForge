package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("dead_letter_record")
@Getter
@Setter
public class DeadLetterRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String eventType;

    private String sourceQueue;

    private Long executionId;

    private String payload;

    private String reason;

    /** PENDING / RESOLVED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
