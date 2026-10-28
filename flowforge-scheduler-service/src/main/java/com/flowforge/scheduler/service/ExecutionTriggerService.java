package com.flowforge.scheduler.service;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.contract.ExecutionCreateRequest;
import com.flowforge.common.contract.WorkflowSnapshot;
import com.flowforge.common.security.LoginUser;
import com.flowforge.scheduler.client.ExecutionClient;
import com.flowforge.scheduler.client.WorkflowClient;
import com.flowforge.scheduler.dto.TriggerRequest;
import com.flowforge.scheduler.dto.TriggerResult;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 手动触发的编排。
 *
 * <p>职责只有三件事：**校验归属与状态 → 取版本快照 → 交给执行服务创建实例**。
 * 调度服务自己不创建执行记录，因为 {@code workflow_execution} 归执行服务所有，
 * 跨库直写会破坏服务边界。
 */
@Service
public class ExecutionTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTriggerService.class);

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TRIGGER_MANUAL = "MANUAL";

    private final WorkflowClient workflowClient;
    private final ExecutionClient executionClient;

    public ExecutionTriggerService(WorkflowClient workflowClient, ExecutionClient executionClient) {
        this.workflowClient = workflowClient;
        this.executionClient = executionClient;
    }

    public TriggerResult trigger(LoginUser user, TriggerRequest request) {
        WorkflowSnapshot snapshot = fetchSnapshot(request.workflowId());

        // 归属校验放在调度侧：执行服务的内部接口不再重复判断
        if (!snapshot.ownerId().equals(user.userId()) && !user.isAdmin()) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        if (snapshot.versionNo() == null || snapshot.versionNo() <= 0) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_PUBLISHED);
        }
        if (STATUS_DISABLED.equals(snapshot.status())) {
            throw new BizException(ErrorCode.WORKFLOW_STATE_CONFLICT, "工作流已停用，无法触发执行");
        }

        ExecutionCreateRequest createRequest = new ExecutionCreateRequest(
                snapshot.workflowId(),
                snapshot.versionId(),
                snapshot.versionNo(),
                snapshot.ownerId(),
                TRIGGER_MANUAL,
                request.requestId(),
                request.timeoutSeconds(),
                snapshot.nodes(),
                snapshot.edges());

        Long executionId = createExecution(createRequest);
        log.info("手动触发成功 workflowId={} versionNo={} executionId={} requestId={}",
                request.workflowId(), snapshot.versionNo(), executionId, request.requestId());
        return new TriggerResult(executionId);
    }

    private WorkflowSnapshot fetchSnapshot(Long workflowId) {
        try {
            ApiResponse<WorkflowSnapshot> response = workflowClient.snapshot(workflowId, null);
            return unwrap(response);
        } catch (FeignException.NotFound ex) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        } catch (FeignException.Conflict ex) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_PUBLISHED);
        } catch (FeignException ex) {
            log.error("调用工作流服务失败 workflowId={} status={}", workflowId, ex.status(), ex);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "工作流服务暂时不可用，请稍后重试");
        }
    }

    private Long createExecution(ExecutionCreateRequest request) {
        try {
            ApiResponse<Map<String, Long>> response = executionClient.create(request);
            Map<String, Long> body = unwrap(response);
            Long executionId = body == null ? null : body.get("executionId");
            if (executionId == null) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "执行服务未返回执行实例");
            }
            return executionId;
        } catch (FeignException ex) {
            log.error("调用执行服务失败 workflowId={} status={}", request.workflowId(), ex.status(), ex);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "执行服务暂时不可用，请稍后重试");
        }
    }

    /** 下游返回业务错误时，把错误码透出给调用方，而不是统一变成 500。 */
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
