package com.flowforge.scheduler.client;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.contract.ExecutionCreateRequest;
import com.flowforge.common.contract.WorkflowSnapshot;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 下游调用的统一门面，挂载熔断器。
 *
 * <p>单独抽成一个 bean 而不是放在 {@code ExecutionTriggerService} 里：
 * Spring AOP 对**同类内部自调用**不生效，注解写在私有方法或自调用方法上等于没写。
 *
 * <p>只对「读」与「可幂等重试」的调用做熔断保护；写操作本身不配自动重试，
 * 重发由用户或周期任务决定，避免一次故障被放大成重复执行。
 */
@Component
public class RemoteCallFacade {

    private static final Logger log = LoggerFactory.getLogger(RemoteCallFacade.class);

    private final WorkflowClient workflowClient;
    private final ExecutionClient executionClient;

    public RemoteCallFacade(WorkflowClient workflowClient, ExecutionClient executionClient) {
        this.workflowClient = workflowClient;
        this.executionClient = executionClient;
    }

    @CircuitBreaker(name = "workflowService", fallbackMethod = "snapshotFallback")
    public WorkflowSnapshot snapshot(Long workflowId, Long versionId) {
        ApiResponse<WorkflowSnapshot> response = workflowClient.snapshot(workflowId, versionId);
        return unwrap(response);
    }

    @CircuitBreaker(name = "executionService", fallbackMethod = "createExecutionFallback")
    public Long createExecution(ExecutionCreateRequest request) {
        ApiResponse<Map<String, Long>> response = executionClient.create(request);
        Map<String, Long> body = unwrap(response);
        Long executionId = body == null ? null : body.get("executionId");
        if (executionId == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "执行服务未返回执行实例");
        }
        return executionId;
    }

    // ---------------------------------------------------------------- 降级

    private WorkflowSnapshot snapshotFallback(Long workflowId, Long versionId, Throwable cause) {
        if (cause instanceof FeignException.NotFound) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        if (cause instanceof FeignException.Conflict) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_PUBLISHED);
        }
        if (cause instanceof BizException bizException) {
            throw bizException;
        }
        log.error("工作流服务调用失败或已熔断 workflowId={}", workflowId, cause);
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "工作流服务暂时不可用，请稍后重试");
    }

    private Long createExecutionFallback(ExecutionCreateRequest request, Throwable cause) {
        if (cause instanceof BizException bizException) {
            throw bizException;
        }
        log.error("执行服务调用失败或已熔断 workflowId={}", request.workflowId(), cause);
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "执行服务暂时不可用，请稍后重试");
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "下游服务返回空响应");
        }
        if (!ErrorCode.OK.code().equals(response.code())) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, response.message());
        }
        return response.data();
    }
}
