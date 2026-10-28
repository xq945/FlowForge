package com.flowforge.execution.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.common.security.LoginUser;
import com.flowforge.execution.dto.AttemptView;
import com.flowforge.execution.dto.ExecutionDetail;
import com.flowforge.execution.dto.ExecutionSummary;
import com.flowforge.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/executions")
@Tag(name = "执行记录", description = "历史查询与失败详情")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping
    @Operation(summary = "执行记录列表", description = "只能看到自己的执行记录")
    public ApiResponse<PageResult<ExecutionSummary>> list(@AuthenticationPrincipal LoginUser user,
                                                          @RequestParam(required = false) Long workflowId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(executionService.list(requireUser(user).userId(), workflowId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "执行详情", description = "含每个节点的状态与失败原因")
    public ApiResponse<ExecutionDetail> detail(@AuthenticationPrincipal LoginUser user,
                                               @PathVariable Long id) {
        return ApiResponse.ok(executionService.detail(requireUser(user).userId(), id));
    }

    @GetMapping("/{id}/nodes/{nodeExecutionId}/attempts")
    @Operation(summary = "节点尝试记录", description = "每一次重试的请求、响应与错误摘要")
    public ApiResponse<List<AttemptView>> attempts(@AuthenticationPrincipal LoginUser user,
                                                   @PathVariable Long id,
                                                   @PathVariable Long nodeExecutionId) {
        return ApiResponse.ok(executionService.attempts(requireUser(user).userId(), id, nodeExecutionId));
    }

    private LoginUser requireUser(LoginUser user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
