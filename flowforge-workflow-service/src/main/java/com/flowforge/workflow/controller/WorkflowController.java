package com.flowforge.workflow.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.common.security.LoginUser;
import com.flowforge.workflow.domain.ValidationResult;
import com.flowforge.workflow.domain.WorkflowStatus;
import com.flowforge.workflow.dto.PublishResult;
import com.flowforge.workflow.dto.WorkflowDetail;
import com.flowforge.workflow.dto.WorkflowSaveRequest;
import com.flowforge.workflow.dto.WorkflowSummary;
import com.flowforge.workflow.service.WorkflowService;
import com.flowforge.workflow.validation.DagValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflows")
@Tag(name = "工作流", description = "定义、版本与发布")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final DagValidator dagValidator;

    public WorkflowController(WorkflowService workflowService, DagValidator dagValidator) {
        this.workflowService = workflowService;
        this.dagValidator = dagValidator;
    }

    @PostMapping
    @Operation(summary = "创建工作流", description = "创建后处于 DRAFT 状态，可继续编辑再发布")
    public ApiResponse<WorkflowDetail> create(@AuthenticationPrincipal LoginUser user,
                                              @Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.create(requireUser(user).userId(), request));
    }

    @GetMapping
    @Operation(summary = "查询我的工作流列表")
    public ApiResponse<PageResult<WorkflowSummary>> list(@AuthenticationPrincipal LoginUser user,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(workflowService.list(requireUser(user).userId(), keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "工作流详情", description = "返回草稿内容与版本历史")
    public ApiResponse<WorkflowDetail> detail(@AuthenticationPrincipal LoginUser user,
                                              @PathVariable Long id) {
        return ApiResponse.ok(workflowService.detail(requireUser(user).userId(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "保存草稿", description = "只覆盖草稿版本，不影响已发布版本")
    public ApiResponse<WorkflowDetail> saveDraft(@AuthenticationPrincipal LoginUser user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.saveDraft(requireUser(user).userId(), id, request));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布新版本", description = "严格校验 DAG，通过后生成不可变版本")
    public ApiResponse<PublishResult> publish(@AuthenticationPrincipal LoginUser user,
                                              @PathVariable Long id) {
        return ApiResponse.ok(workflowService.publish(requireUser(user).userId(), id));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用")
    public ApiResponse<Void> enable(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        workflowService.changeStatus(requireUser(user).userId(), id, WorkflowStatus.PUBLISHED);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用", description = "只影响新的触发，在途实例继续执行")
    public ApiResponse<Void> disable(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        workflowService.changeStatus(requireUser(user).userId(), id, WorkflowStatus.DISABLED);
        return ApiResponse.ok();
    }

    @PostMapping("/validate")
    @Operation(summary = "校验工作流定义", description = "不落库，仅返回全部校验错误，供编辑器实时提示")
    public ApiResponse<ValidationResult> validate(@AuthenticationPrincipal LoginUser user,
                                                  @Valid @RequestBody WorkflowSaveRequest request) {
        requireUser(user);
        return ApiResponse.ok(dagValidator.validate(request.nodes(), request.edges(), true));
    }

    private LoginUser requireUser(LoginUser user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
