package com.flowforge.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("schedule_job")
@Getter
@Setter
public class ScheduleJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** 留空表示始终跟随最新的发布版本 */
    private Long workflowVersionId;

    private Long ownerId;

    private String name;

    private String cronExpr;

    private String timezone;

    private Integer enabled;

    private LocalDateTime nextFireTime;

    private LocalDateTime lastFireTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
