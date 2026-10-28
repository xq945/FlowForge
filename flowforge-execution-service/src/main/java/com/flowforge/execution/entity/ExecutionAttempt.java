package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("execution_attempt")
@Getter
@Setter
public class ExecutionAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private Long nodeExecutionId;

    /** 从 1 开始，与 nodeExecutionId 组成唯一键 */
    private Integer attemptNo;

    private String status;

    private String errorCode;

    private String errorMessage;

    /** 结果未知（如读超时）：对外部系统的副作用无法确认 */
    private Integer uncertain;

    private String requestSummary;

    private String responseSummary;

    private Integer durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
