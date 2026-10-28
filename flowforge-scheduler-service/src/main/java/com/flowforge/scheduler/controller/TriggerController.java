package com.flowforge.scheduler.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.security.LoginUser;
import com.flowforge.scheduler.dto.TriggerRequest;
import com.flowforge.scheduler.dto.TriggerResult;
import com.flowforge.scheduler.service.ExecutionTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/executions")
@Tag(name = "执行触发", description = "手动执行工作流")
public class TriggerController {

    private final ExecutionTriggerService triggerService;

    public TriggerController(ExecutionTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @PostMapping
    @Operation(summary = "手动执行工作流",
            description = "携带相同 requestId 的重复请求只会创建一个执行实例")
    public ApiResponse<TriggerResult> trigger(@AuthenticationPrincipal LoginUser user,
                                              @Valid @RequestBody TriggerRequest request) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(triggerService.trigger(user, request));
    }
}
