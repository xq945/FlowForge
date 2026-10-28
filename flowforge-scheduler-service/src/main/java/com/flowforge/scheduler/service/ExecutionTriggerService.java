package com.flowforge.scheduler.service;

import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.contract.ExecutionCreateRequest;
import com.flowforge.common.contract.WorkflowSnapshot;
import com.flowforge.common.security.LoginUser;
import com.flowforge.scheduler.client.RemoteCallFacade;
import com.flowforge.scheduler.dto.TriggerRequest;
import com.flowforge.scheduler.dto.TriggerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 执行触发的编排。
 *
 * <p>职责只有三件事：**校验状态 → 取版本快照 → 交给执行服务创建实例**。
 * 调度服务自己不创建执行记录，因为 {@code workflow_execution} 归执行服务所有。
 *
 * <p>两条入口共用同一套创建逻辑，区别只在归属校验：
 * 用户触发要校验「这是不是你的工作流」，周期触发以系统身份运行，不做归属判断。
 */
@Service
public class ExecutionTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTriggerService.class);

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final String TRIGGER_SCHEDULE = "SCHEDULE";

    private final RemoteCallFacade remoteCall;

    public ExecutionTriggerService(RemoteCallFacade remoteCall) {
        this.remoteCall = remoteCall;
    }

    /** 用户手动触发。 */
    public TriggerResult trigger(LoginUser user, TriggerRequest request) {
        WorkflowSnapshot snapshot = remoteCall.snapshot(request.workflowId(), null);

        if (!snapshot.ownerId().equals(user.userId()) && !user.isAdmin()) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        Long executionId = createFromSnapshot(snapshot, TRIGGER_MANUAL, request.requestId(),
                request.timeoutSeconds());

        log.info("手动触发成功 workflowId={} versionNo={} executionId={} requestId={}",
                request.workflowId(), snapshot.versionNo(), executionId, request.requestId());
        return new TriggerResult(executionId);
    }

    /**
     * 周期触发。
     *
     * <p>{@code triggerKey} 用 {@code jobId:fireTime}，与 {@code workflow_execution.uk_trigger}
     * 组成第二道防重复——即使调度侧唯一键被绕过，也不会创建两个实例。
     */
    public Long triggerBySchedule(Long workflowId, Long workflowVersionId, Long jobId, String fireTime) {
        WorkflowSnapshot snapshot = remoteCall.snapshot(workflowId, workflowVersionId);
        String triggerKey = jobId + ":" + fireTime;
        Long executionId = createFromSnapshot(snapshot, TRIGGER_SCHEDULE, triggerKey, null);

        log.info("周期触发成功 workflowId={} versionNo={} executionId={} triggerKey={}",
                workflowId, snapshot.versionNo(), executionId, triggerKey);
        return executionId;
    }

    private Long createFromSnapshot(WorkflowSnapshot snapshot, String triggerType,
                                    String triggerKey, Integer timeoutSeconds) {
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
                triggerType,
                triggerKey,
                timeoutSeconds,
                snapshot.nodes(),
                snapshot.edges());

        return remoteCall.createExecution(createRequest);
    }
}
