package com.flowforge.workflow.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.contract.WorkflowSnapshot;
import com.flowforge.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间接口。
 *
 * <p>不经过网关（调度服务用 Nacos 服务名直连），但仍要求携带用户 JWT：
 * 调用方在用户请求线程内转发 Authorization 头，因此这里的身份仍是真实用户，
 * 而不是「内网即可信」的假设。
 */
@RestController
@RequestMapping("/internal/workflows")
@Tag(name = "内部接口", description = "仅供其它服务调用")
public class InternalWorkflowController {

    private final WorkflowService workflowService;

    public InternalWorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/{workflowId}/snapshot")
    @Operation(summary = "读取版本快照", description = "versionId 为空时返回当前发布版本")
    public ApiResponse<WorkflowSnapshot> snapshot(@PathVariable Long workflowId,
                                                  @RequestParam(required = false) Long versionId) {
        return ApiResponse.ok(workflowService.snapshot(workflowId, versionId));
    }
}
