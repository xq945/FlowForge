package com.flowforge.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("schedule_execution")
@Getter
@Setter
public class ScheduleExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    /** 本次触发对应的计划时间，与 jobId 组成唯一键 */
    private LocalDateTime fireTime;

    /** CREATED / FAILED */
    private String status;

    private Long workflowExecutionId;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
