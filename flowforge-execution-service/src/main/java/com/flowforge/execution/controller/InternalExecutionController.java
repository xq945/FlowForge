package com.flowforge.execution.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.execution.domain.TriggerType;
import com.flowforge.common.contract.ExecutionCreateRequest;
import com.flowforge.execution.engine.CreateExecutionCommand;
import com.flowforge.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务间接口：由调度服务调用创建执行实例。
 *
 * <p>不做归属校验——归属在用户请求入口（调度服务）已经确认过，
 * 这里拿到的 ownerId 是**可信的调用方传入**，所以接口本身仍要求有效 JWT。
 */
@RestController
@RequestMapping("/internal/executions")
@Tag(name = "内部接口", description = "仅供其它服务调用")
public class InternalExecutionController {

    private final ExecutionService executionService;

    public InternalExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    @Operation(summary = "创建执行实例", description = "相同 triggerType + triggerKey 幂等，重复请求返回同一个实例")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ExecutionCreateRequest request) {
        TriggerType triggerType = parseTriggerType(request.triggerType());

        CreateExecutionCommand command = new CreateExecutionCommand(
                request.workflowId(),
                request.workflowVersionId(),
                request.versionNo(),
                request.ownerId(),
                triggerType.name(),
                request.triggerKey(),
                request.nodes(),
                request.edges(),
                request.timeoutSeconds());

        Long executionId = executionService.start(command);
        return ApiResponse.ok(Map.of("executionId", executionId));
    }

    private TriggerType parseTriggerType(String value) {
        try {
            return TriggerType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "不支持的触发类型：" + value);
        }
    }
}
