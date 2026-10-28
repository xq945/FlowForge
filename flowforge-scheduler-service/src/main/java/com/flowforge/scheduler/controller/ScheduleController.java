package com.flowforge.scheduler.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.security.LoginUser;
import com.flowforge.scheduler.dto.CreateScheduleRequest;
import com.flowforge.scheduler.dto.ScheduleJobView;
import com.flowforge.scheduler.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedules")
@Tag(name = "周期任务", description = "cron 配置与启停")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    @Operation(summary = "创建周期任务", description = "cron 为 6 位格式：秒 分 时 日 月 周")
    public ApiResponse<ScheduleJobView> create(@AuthenticationPrincipal LoginUser user,
                                               @Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.ok(scheduleService.create(requireUser(user), request));
    }

    @GetMapping
    @Operation(summary = "查询我的周期任务")
    public ApiResponse<List<ScheduleJobView>> list(@AuthenticationPrincipal LoginUser user,
                                                   @RequestParam(required = false) Long workflowId) {
        return ApiResponse.ok(scheduleService.list(requireUser(user), workflowId));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用")
    public ApiResponse<Void> enable(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        scheduleService.setEnabled(requireUser(user), id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用")
    public ApiResponse<Void> disable(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        scheduleService.setEnabled(requireUser(user), id, false);
        return ApiResponse.ok();
    }

    private LoginUser requireUser(LoginUser user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
