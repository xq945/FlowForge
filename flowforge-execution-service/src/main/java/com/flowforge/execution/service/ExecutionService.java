package com.flowforge.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.execution.dto.AttemptView;
import com.flowforge.execution.dto.ExecutionDetail;
import com.flowforge.execution.dto.ExecutionSummary;
import com.flowforge.execution.dto.NodeExecutionView;
import com.flowforge.execution.engine.CreateExecutionCommand;
import com.flowforge.execution.engine.ExecutionEngine;
import com.flowforge.execution.entity.ExecutionAttempt;
import com.flowforge.execution.entity.NodeExecution;
import com.flowforge.execution.entity.WorkflowExecution;
import com.flowforge.execution.mapper.ExecutionAttemptMapper;
import com.flowforge.execution.mapper.NodeExecutionMapper;
import com.flowforge.execution.mapper.WorkflowExecutionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ExecutionService {

    private final ExecutionEngine engine;
    private final WorkflowExecutionMapper executionMapper;
    private final NodeExecutionMapper nodeMapper;
    private final ExecutionAttemptMapper attemptMapper;

    public ExecutionService(ExecutionEngine engine,
                            WorkflowExecutionMapper executionMapper,
                            NodeExecutionMapper nodeMapper,
                            ExecutionAttemptMapper attemptMapper) {
        this.engine = engine;
        this.executionMapper = executionMapper;
        this.nodeMapper = nodeMapper;
        this.attemptMapper = attemptMapper;
    }

    /**
     * 创建执行实例。
     *
     * <p>起始节点的可执行消息已经在创建事务里写进 Outbox，由发布器投递，
     * 这里不需要（也不应该）再去驱动执行。
     */
    public Long start(CreateExecutionCommand command) {
        return engine.createExecution(command);
    }

    public PageResult<ExecutionSummary> list(Long ownerId, Long workflowId, String status, long page, long size) {
        Page<WorkflowExecution> query = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<WorkflowExecution> wrapper = new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getOwnerId, ownerId)
                .eq(workflowId != null, WorkflowExecution::getWorkflowId, workflowId)
                .eq(StringUtils.hasText(status), WorkflowExecution::getStatus, status)
                .orderByDesc(WorkflowExecution::getId);
        Page<WorkflowExecution> result = executionMapper.selectPage(query, wrapper);

        List<ExecutionSummary> records = result.getRecords().stream()
                .map(execution -> new ExecutionSummary(
                        execution.getId(),
                        execution.getWorkflowId(),
                        execution.getVersionNo(),
                        execution.getTriggerType(),
                        execution.getStatus(),
                        execution.getTotalNodes(),
                        execution.getFinishedNodes(),
                        execution.getStartedAt(),
                        execution.getFinishedAt(),
                        execution.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), query.getCurrent(), query.getSize());
    }

    public ExecutionDetail detail(Long ownerId, Long executionId) {
        WorkflowExecution execution = requireOwned(ownerId, executionId);
        List<NodeExecutionView> nodes = nodeMapper.selectList(new LambdaQueryWrapper<NodeExecution>()
                        .eq(NodeExecution::getExecutionId, executionId)
                        .orderByAsc(NodeExecution::getId))
                .stream()
                .map(node -> new NodeExecutionView(
                        node.getId(),
                        node.getNodeKey(),
                        node.getNodeType(),
                        node.getNodeName(),
                        node.getStatus(),
                        node.getRetryCount(),
                        node.getMaxRetry(),
                        node.getAttemptNo(),
                        node.getNextRetryAt(),
                        node.getOutputJson(),
                        node.getErrorCode(),
                        node.getErrorMessage(),
                        node.getStartedAt(),
                        node.getFinishedAt()))
                .toList();

        return new ExecutionDetail(
                execution.getId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersionId(),
                execution.getVersionNo(),
                execution.getTriggerType(),
                execution.getStatus(),
                execution.getTotalNodes(),
                execution.getFinishedNodes(),
                execution.getErrorCode(),
                execution.getErrorMessage(),
                execution.getDeadlineAt(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                nodes);
    }

    public List<AttemptView> attempts(Long ownerId, Long executionId, Long nodeExecutionId) {
        requireOwned(ownerId, executionId);
        NodeExecution node = nodeMapper.selectById(nodeExecutionId);
        if (node == null || !node.getExecutionId().equals(executionId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "节点执行记录不存在");
        }

        return attemptMapper.selectList(new LambdaQueryWrapper<ExecutionAttempt>()
                        .eq(ExecutionAttempt::getNodeExecutionId, nodeExecutionId)
                        .orderByAsc(ExecutionAttempt::getAttemptNo))
                .stream()
                .map(attempt -> new AttemptView(
                        attempt.getId(),
                        attempt.getAttemptNo(),
                        attempt.getStatus(),
                        attempt.getErrorCode(),
                        attempt.getErrorMessage(),
                        attempt.getUncertain() != null && attempt.getUncertain() == 1,
                        attempt.getRequestSummary(),
                        attempt.getResponseSummary(),
                        attempt.getDurationMs(),
                        attempt.getStartedAt(),
                        attempt.getFinishedAt()))
                .toList();
    }

    private WorkflowExecution requireOwned(Long ownerId, Long executionId) {
        WorkflowExecution execution = executionMapper.selectById(executionId);
        if (execution == null || !execution.getOwnerId().equals(ownerId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "执行记录不存在");
        }
        return execution;
    }
}
