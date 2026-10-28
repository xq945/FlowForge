package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("execution_event")
@Getter
@Setter
public class ExecutionEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private Long nodeExecutionId;

    /** EXECUTION_CREATED / NODE_READY / NODE_STARTED / NODE_SUCCEEDED / NODE_FAILED / NODE_SKIPPED / EXECUTION_FINISHED */
    private String eventType;

    private String payload;

    private LocalDateTime createdAt;
}
