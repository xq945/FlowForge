package com.flowforge.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建周期任务。
 *
 * @param cronExpr 6 位标准 cron：秒 分 时 日 月 周
 */
public record CreateScheduleRequest(

        @NotNull(message = "workflowId 不能为空")
        Long workflowId,

        @NotBlank(message = "任务名称不能为空")
        @Size(max = 64, message = "任务名称不能超过 64 个字符")
        String name,

        @NotBlank(message = "cron 表达式不能为空")
        @Size(max = 64, message = "cron 表达式过长")
        String cronExpr,

        @Size(max = 64, message = "时区标识过长")
        String timezone
) {
}
