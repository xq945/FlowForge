package com.flowforge.execution.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import com.flowforge.common.contract.NodeType;
import com.flowforge.common.util.SensitiveDataMasker;
import com.flowforge.execution.domain.ExecutionStatus;
import com.flowforge.execution.domain.NodeStatus;
import com.flowforge.execution.entity.ExecutionAttempt;
import com.flowforge.execution.entity.ExecutionEvent;
import com.flowforge.execution.entity.NodeExecution;
import com.flowforge.execution.entity.WorkflowExecution;
import com.flowforge.execution.mapper.ExecutionAttemptMapper;
import com.flowforge.execution.mapper.ExecutionEventMapper;
import com.flowforge.execution.mapper.NodeExecutionMapper;
import com.flowforge.execution.mapper.WorkflowExecutionMapper;
import com.flowforge.execution.messaging.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 执行编排。
 *
 * <p>职责边界：执行器只负责「怎么做」，这里负责「做完之后系统状态怎么变」——
 * 抢占、落结果、重算可达性、推进后继、判定终结。
 *
 * <p>三个阶段始终成立：
 * <ol>
 *   <li>抢占与状态迁移都用带前置条件的 UPDATE，重复消息、并发消费天然被吸收；</li>
 *   <li>推进逻辑在实例行的行锁内执行，多个节点同时完成也不会让实例状态打架；</li>
 *   <li>每一步都能从数据库状态重算，进程崩溃后下一轮扫描可以继续。</li>
 * </ol>
 */
@Service
public class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private static final int MAX_SUMMARY_LENGTH = 2048;

    private final WorkflowExecutionMapper executionMapper;
    private final NodeExecutionMapper nodeMapper;
    private final ExecutionAttemptMapper attemptMapper;
    private final ExecutionEventMapper eventMapper;
    private final Map<NodeType, NodeExecutor> executors;
    private final TransactionTemplate transactionTemplate;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ExecutionEngine(WorkflowExecutionMapper executionMapper,
                           NodeExecutionMapper nodeMapper,
                           ExecutionAttemptMapper attemptMapper,
                           ExecutionEventMapper eventMapper,
                           List<NodeExecutor> nodeExecutors,
                           TransactionTemplate transactionTemplate,
                           OutboxWriter outboxWriter,
                           ObjectMapper objectMapper) {
        this.executionMapper = executionMapper;
        this.nodeMapper = nodeMapper;
        this.attemptMapper = attemptMapper;
        this.eventMapper = eventMapper;
        this.executors = nodeExecutors.stream()
                .collect(Collectors.toMap(NodeExecutor::type, Function.identity()));
        this.transactionTemplate = transactionTemplate;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ 创建实例

    /**
     * 幂等创建执行实例。
     *
     * <p>先查一次减少无谓冲突；并发下靠 {@code uk_trigger} 唯一键兜底——
     * 冲突时把事务整体回滚，再去查回已经存在的那条。
     */
    public Long createExecution(CreateExecutionCommand command) {
        WorkflowExecution existing = findByIdempotencyKey(command.triggerType(), command.triggerKey());
        if (existing != null) {
            log.debug("命中幂等键，复用已有实例 executionId={}", existing.getId());
            return existing.getId();
        }
        try {
            return transactionTemplate.execute(status -> doCreate(command));
        } catch (DuplicateKeyException ex) {
            WorkflowExecution raced = findByIdempotencyKey(command.triggerType(), command.triggerKey());
            if (raced == null) {
                throw ex;
            }
            log.debug("并发创建冲突，复用实例 executionId={}", raced.getId());
            return raced.getId();
        }
    }

    private Long doCreate(CreateExecutionCommand command) {
        List<NodeSpec> nodes = command.nodes();
        List<EdgeSpec> edges = command.edges() == null ? List.of() : command.edges();
        Set<String> roots = rootsOf(nodes, edges);
        LocalDateTime now = LocalDateTime.now();
        int timeoutSeconds = command.timeoutSeconds() == null || command.timeoutSeconds() <= 0
                ? CreateExecutionCommand.DEFAULT_TIMEOUT_SECONDS
                : command.timeoutSeconds();

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(command.workflowId());
        execution.setWorkflowVersionId(command.workflowVersionId());
        execution.setVersionNo(command.versionNo());
        execution.setOwnerId(command.ownerId());
        execution.setTriggerType(command.triggerType());
        execution.setTriggerKey(command.triggerKey());
        execution.setStatus(ExecutionStatus.RUNNING.name());
        execution.setCancelRequested(0);
        execution.setTotalNodes(nodes.size());
        execution.setFinishedNodes(0);
        execution.setEdgesSnapshot(writeJson(edges));
        execution.setDeadlineAt(now.plusSeconds(timeoutSeconds));
        execution.setStartedAt(now);
        executionMapper.insert(execution);

        List<NodeExecution> startNodes = new ArrayList<>();
        for (NodeSpec spec : nodes) {
            NodeExecution node = new NodeExecution();
            node.setExecutionId(execution.getId());
            node.setNodeKey(spec.nodeKey());
            node.setNodeType(spec.nodeType().toUpperCase());
            node.setNodeName(spec.name() == null ? spec.nodeKey() : spec.name());
            boolean isRoot = roots.contains(spec.nodeKey());
            node.setStatus(isRoot ? NodeStatus.READY.name() : NodeStatus.WAITING.name());
            node.setRetryCount(0);
            node.setMaxRetry(spec.retryMaxOrDefault());
            node.setRetryInitialMs(spec.retryInitialOrDefault());
            node.setRetryMaxMs(spec.retryMaxDelayOrDefault());
            node.setTimeoutMs(spec.timeoutOrDefault());
            node.setAttemptNo(0);
            node.setConfigSnapshot(writeJson(spec.config() == null ? Map.of() : spec.config()));
            nodeMapper.insert(node);
            if (isRoot) {
                startNodes.add(node);
            }
        }

        appendEvent(execution.getId(), null, "EXECUTION_CREATED",
                Map.of("workflowId", command.workflowId(),
                        "versionNo", command.versionNo(),
                        "totalNodes", nodes.size()));

        // 起始节点的可执行消息与实例创建在同一个事务里落进 Outbox
        for (NodeExecution startNode : startNodes) {
            outboxWriter.appendNodeReady(execution.getId(), startNode.getId(),
                    command.versionNo(), startNode.getNodeKey());
        }

        log.info("执行实例创建 executionId={} workflowId={} versionNo={} nodes={} 起始节点={}",
                execution.getId(), command.workflowId(), command.versionNo(), nodes.size(), startNodes.size());
        return execution.getId();
    }

    // ------------------------------------------------------------------ 调度与执行

    /**
     * 执行单个节点。
     *
     * <p>由消息消费者调用。方法本身不负责「下一个节点由谁执行」——
     * 后继节点通过 Outbox 投递，投递、确认、重试都交给消息层。
     */
    public void runNode(Long nodeExecutionId) {
        if (nodeMapper.claimForRun(nodeExecutionId) == 0) {
            // 已被别的线程/实例抢走，或状态不是 READY：正常幂等场景，直接丢弃
            return;
        }

        NodeExecution node = nodeMapper.selectById(nodeExecutionId);
        if (node == null) {
            return;
        }

        NodeResult result;
        try {
            NodeContext context = buildContext(node);
            NodeExecutor executor = executors.get(NodeType.from(node.getNodeType()));
            result = executor == null
                    ? NodeResult.failure("UNKNOWN_NODE_TYPE", "不支持的节点类型：" + node.getNodeType(),
                    false, false, null, null)
                    : executor.execute(context);
        } catch (Exception ex) {
            log.error("节点执行异常 executionId={} nodeKey={}", node.getExecutionId(), node.getNodeKey(), ex);
            result = NodeResult.failure("EXECUTOR_ERROR", "节点执行异常",
                    false, false, null, null);
        }

        completeNode(node, result);
    }

    /**
     * 落结果并推进。整体在一个事务里，开头先锁住实例行，
     * 保证「判定是否可推进」与「真正推进」之间不会有别的线程插进来。
     */
    private List<Long> completeNode(NodeExecution node, NodeResult result) {
        return transactionTemplate.execute(status -> {
            WorkflowExecution execution = executionMapper.selectForUpdate(node.getExecutionId());
            if (execution == null) {
                return List.of();
            }
            if (ExecutionStatus.valueOf(execution.getStatus()).isTerminal()) {
                log.debug("实例已终结，丢弃节点结果 executionId={} nodeKey={}",
                        execution.getId(), node.getNodeKey());
                return List.of();
            }

            saveAttempt(node, result);

            int retryCount = node.getRetryCount() == null ? 0 : node.getRetryCount();
            int maxRetry = node.getMaxRetry() == null ? 0 : node.getMaxRetry();

            String target;
            if (result.success()) {
                target = NodeStatus.SUCCESS.name();
            } else if (result.retryable() && retryCount < maxRetry) {
                target = NodeStatus.RETRY_WAIT.name();
            } else {
                target = NodeStatus.FAILED.name();
            }

            String output = result.output() == null ? null : writeJson(result.output());
            int updated = nodeMapper.transition(node.getId(), NodeStatus.RUNNING.name(), target,
                    output,
                    result.errorCode(),
                    SensitiveDataMasker.maskAndTruncate(result.errorMessage(), MAX_SUMMARY_LENGTH));
            if (updated == 0) {
                // 状态已被并发改动（例如实例被取消），不再推进
                return List.of();
            }

            appendEvent(execution.getId(), node.getId(), "NODE_" + target.replace("_WAIT", ""),
                    Map.of("nodeKey", node.getNodeKey(),
                            "attemptNo", node.getAttemptNo() == null ? 0 : node.getAttemptNo(),
                            "errorCode", result.errorCode() == null ? "" : result.errorCode()));

            if (NodeStatus.RETRY_WAIT.name().equals(target)) {
                LocalDateTime next = nextRetryAt(node, retryCount);
                nodeMapper.scheduleRetry(node.getId(), next);
                log.info("节点进入退避等待 executionId={} nodeKey={} retry={}/{} nextAt={}",
                        execution.getId(), node.getNodeKey(), retryCount + 1, maxRetry, next);
                return List.of();
            }

            List<Long> newlyReady = advance(execution);
            // 后继节点的可执行消息与节点终态在同一事务内写进 Outbox
            for (Long readyId : newlyReady) {
                NodeExecution ready = nodeMapper.selectById(readyId);
                if (ready != null) {
                    outboxWriter.appendNodeReady(execution.getId(), ready.getId(),
                            execution.getVersionNo(), ready.getNodeKey());
                }
            }
            return newlyReady;
        });
    }

    /**
     * 推进：重算可达性 → 跳过不可达 → 失败传播 → 唤醒可执行节点 → 判定终结。
     *
     * <p>调用方必须已经持有实例行锁（{@code selectForUpdate}）。
     */
    private List<Long> advance(WorkflowExecution execution) {
        ExecutionGraph graph = graphOf(execution);

        reloadAndSkipUnreachable(execution, graph);
        propagateFailures(execution, graph);
        List<Long> ready = wakeUpReadyNodes(execution, graph);
        settleIfFinished(execution);
        return ready;
    }

    private void reloadAndSkipUnreachable(WorkflowExecution execution, ExecutionGraph graph) {
        List<NodeExecution> nodes = loadNodes(execution.getId());
        Set<String> reachable = graph.reachableNodes(resolvedConditions(nodes));

        for (NodeExecution node : nodes) {
            if (!NodeStatus.WAITING.name().equals(node.getStatus())) {
                continue;
            }
            if (reachable.contains(node.getNodeKey())) {
                continue;
            }
            if (nodeMapper.transition(node.getId(), NodeStatus.WAITING.name(), NodeStatus.SKIPPED.name(),
                    null, null, null) > 0) {
                appendEvent(execution.getId(), node.getId(), "NODE_SKIPPED",
                        Map.of("nodeKey", node.getNodeKey(), "reason", "分支未选中"));
            }
        }
    }

    private void propagateFailures(WorkflowExecution execution, ExecutionGraph graph) {
        List<NodeExecution> nodes = loadNodes(execution.getId());
        Map<String, NodeExecution> byKey = index(nodes);

        for (NodeExecution node : nodes) {
            if (!NodeStatus.FAILED.name().equals(node.getStatus())) {
                continue;
            }
            for (String descendant : graph.descendantsOf(node.getNodeKey())) {
                NodeExecution target = byKey.get(descendant);
                if (target == null || !isCancellable(target.getStatus())) {
                    continue;
                }
                if (nodeMapper.transition(target.getId(), target.getStatus(), NodeStatus.CANCELLED.name(),
                        null, "UPSTREAM_FAILED", "上游节点失败，终止传播") > 0) {
                    appendEvent(execution.getId(), target.getId(), "NODE_CANCELLED",
                            Map.of("nodeKey", target.getNodeKey(), "reason", "上游失败"));
                }
            }
        }
    }

    private List<Long> wakeUpReadyNodes(WorkflowExecution execution, ExecutionGraph graph) {
        List<NodeExecution> nodes = loadNodes(execution.getId());
        Map<String, NodeExecution> byKey = index(nodes);
        Set<String> reachable = graph.reachableNodes(resolvedConditions(nodes));

        List<Long> ready = new ArrayList<>();
        for (NodeExecution node : nodes) {
            if (!NodeStatus.WAITING.name().equals(node.getStatus())) {
                continue;
            }
            if (!reachable.contains(node.getNodeKey())) {
                continue;
            }
            boolean allPredecessorsDone = graph.predecessorsOf(node.getNodeKey()).stream()
                    .map(byKey::get)
                    .filter(java.util.Objects::nonNull)
                    .allMatch(pred -> NodeStatus.SUCCESS.name().equals(pred.getStatus())
                            || NodeStatus.SKIPPED.name().equals(pred.getStatus()));
            if (!allPredecessorsDone) {
                continue;
            }
            if (nodeMapper.transition(node.getId(), NodeStatus.WAITING.name(), NodeStatus.READY.name(),
                    null, null, null) > 0) {
                ready.add(node.getId());
                appendEvent(execution.getId(), node.getId(), "NODE_READY",
                        Map.of("nodeKey", node.getNodeKey()));
            }
        }
        return ready;
    }

    private void settleIfFinished(WorkflowExecution execution) {
        List<NodeExecution> nodes = loadNodes(execution.getId());
        if (nodes.isEmpty()) {
            return;
        }
        long terminal = nodes.stream()
                .filter(node -> NodeStatus.valueOf(node.getStatus()).isTerminal())
                .count();
        executionMapper.updateFinishedNodes(execution.getId(), (int) terminal);

        if (terminal < nodes.size()) {
            return;
        }

        String finalStatus;
        String errorCode = null;
        String errorMessage = null;
        if (nodes.stream().anyMatch(n -> NodeStatus.FAILED.name().equals(n.getStatus()))) {
            NodeExecution failed = nodes.stream()
                    .filter(n -> NodeStatus.FAILED.name().equals(n.getStatus()))
                    .findFirst().orElse(null);
            finalStatus = ExecutionStatus.FAILED.name();
            errorCode = failed == null ? "NODE_FAILED" : failed.getErrorCode();
            errorMessage = failed == null ? "存在失败节点" : failed.getErrorMessage();
        } else if (nodes.stream().anyMatch(n -> NodeStatus.CANCELLED.name().equals(n.getStatus()))) {
            finalStatus = ExecutionStatus.CANCELLED.name();
            errorMessage = "存在被取消的节点";
        } else {
            finalStatus = ExecutionStatus.SUCCESS.name();
        }

        String maskedError = SensitiveDataMasker.maskAndTruncate(errorMessage, MAX_SUMMARY_LENGTH);
        if (executionMapper.finishIfRunning(execution.getId(), finalStatus, errorCode, maskedError) > 0) {
            appendEvent(execution.getId(), null, "EXECUTION_FINISHED",
                    Map.of("status", finalStatus));
            // 终结事件同样走 Outbox，保证「实例已终结」与「通知已发出」最终一致
            outboxWriter.appendWorkflowFinished(execution.getId(), execution.getOwnerId(),
                    finalStatus, errorCode, maskedError);
            log.info("执行实例终结 executionId={} status={}", execution.getId(), finalStatus);
        }
    }

    // ------------------------------------------------------------------ 重试

    /**
     * 实例级超时收敛。
     *
     * <p>不依赖内存计时器：进程崩了计时器就没了，只有数据库里的 {@code deadline_at} 是可信的。
     * 超时后把尚未执行的节点统一作废，正在跑的节点任其结束（结果会被丢弃），
     * 保证实例一定能收敛到终态而不是永远挂在 RUNNING。
     */
    public int timeoutExpired(int limit) {
        List<WorkflowExecution> expired = executionMapper.selectTimedOut(limit);
        int count = 0;
        for (WorkflowExecution candidate : expired) {
            Boolean expiredNow = transactionTemplate.execute(status -> expireOne(candidate.getId()));
            if (Boolean.TRUE.equals(expiredNow)) {
                count++;
            }
        }
        return count;
    }

    private boolean expireOne(Long executionId) {
        WorkflowExecution execution = executionMapper.selectForUpdate(executionId);
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return false;
        }

        int cancelled = nodeMapper.cancelPendingNodes(executionId);
        int updated = executionMapper.finishIfRunning(executionId,
                ExecutionStatus.TIMED_OUT.name(), "EXECUTION_TIMEOUT", "工作流执行超过截止时间");
        if (updated == 0) {
            return false;
        }

        appendEvent(executionId, null, "EXECUTION_TIMED_OUT",
                Map.of("cancelledNodes", cancelled));
        outboxWriter.appendWorkflowFinished(executionId, execution.getOwnerId(),
                ExecutionStatus.TIMED_OUT.name(), "EXECUTION_TIMEOUT", "工作流执行超过截止时间");
        log.warn("执行实例超时 executionId={} 作废未执行节点数={}", executionId, cancelled);
        return true;
    }

    /** 退避到期，把节点放回待执行。多实例下靠条件更新保证只有一个实例放行成功。 */
    public void releaseDueRetries(int batchSize) {
        List<NodeExecution> due = nodeMapper.selectList(new LambdaQueryWrapper<NodeExecution>()
                .eq(NodeExecution::getStatus, NodeStatus.RETRY_WAIT.name())
                .le(NodeExecution::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(NodeExecution::getNextRetryAt)
                .last("LIMIT " + Math.max(1, batchSize)));

        for (NodeExecution node : due) {
            if (nodeMapper.releaseForRetry(node.getId()) <= 0) {
                continue;
            }
            transactionTemplate.executeWithoutResult(status -> {
                WorkflowExecution execution = executionMapper.selectById(node.getExecutionId());
                if (execution == null || ExecutionStatus.valueOf(execution.getStatus()).isTerminal()) {
                    return;
                }
                outboxWriter.appendNodeReady(node.getExecutionId(), node.getId(),
                        execution.getVersionNo(), node.getNodeKey());
            });
        }
    }

    private LocalDateTime nextRetryAt(NodeExecution node, int retryCount) {
        long initial = node.getRetryInitialMs() == null ? 1_000 : node.getRetryInitialMs();
        long max = node.getRetryMaxMs() == null ? 60_000 : node.getRetryMaxMs();
        long delay = Math.min(max, initial * (1L << Math.min(retryCount, 10)));
        // 加入 ±20% 抖动，避免一批同时失败的节点在同一时刻集中重试
        long jitter = (long) (delay * 0.2 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        return LocalDateTime.now().plus(Duration.ofMillis(Math.max(0, delay + jitter)));
    }

    // ------------------------------------------------------------------ 辅助

    private NodeContext buildContext(NodeExecution node) {
        Map<String, Object> config = readJsonMap(node.getConfigSnapshot());
        return new NodeContext(
                node.getExecutionId(),
                node.getId(),
                node.getNodeKey(),
                node.getNodeType(),
                config,
                node.getAttemptNo() == null ? 1 : node.getAttemptNo(),
                collectUpstreamOutput(node));
    }

    /** 汇总直接前置节点的输出，供条件节点比较；多个前驱的输出按顺序合并。 */
    private Map<String, Object> collectUpstreamOutput(NodeExecution node) {
        WorkflowExecution execution = executionMapper.selectById(node.getExecutionId());
        if (execution == null) {
            return Map.of();
        }
        ExecutionGraph graph = graphOf(execution);
        List<NodeExecution> nodes = loadNodes(execution.getId());
        Map<String, NodeExecution> byKey = index(nodes);

        Map<String, Object> merged = new HashMap<>();
        for (String predecessor : graph.predecessorsOf(node.getNodeKey())) {
            NodeExecution pred = byKey.get(predecessor);
            if (pred == null || !NodeStatus.SUCCESS.name().equals(pred.getStatus())) {
                continue;
            }
            merged.putAll(readJsonMap(pred.getOutputJson()));
        }
        return merged;
    }

    private void saveAttempt(NodeExecution node, NodeResult result) {
        ExecutionAttempt attempt = new ExecutionAttempt();
        attempt.setExecutionId(node.getExecutionId());
        attempt.setNodeExecutionId(node.getId());
        attempt.setAttemptNo(node.getAttemptNo() == null ? 1 : node.getAttemptNo());
        attempt.setStatus(result.success() ? "SUCCESS" : "FAILED");
        attempt.setErrorCode(result.errorCode());
        attempt.setErrorMessage(SensitiveDataMasker.maskAndTruncate(result.errorMessage(), MAX_SUMMARY_LENGTH));
        attempt.setUncertain(result.uncertain() ? 1 : 0);
        attempt.setRequestSummary(SensitiveDataMasker.maskAndTruncate(result.requestSummary(), MAX_SUMMARY_LENGTH));
        attempt.setResponseSummary(SensitiveDataMasker.maskAndTruncate(result.responseSummary(), MAX_SUMMARY_LENGTH));
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setFinishedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
    }

    private void appendEvent(Long executionId, Long nodeExecutionId, String eventType, Map<String, Object> payload) {
        ExecutionEvent event = new ExecutionEvent();
        event.setExecutionId(executionId);
        event.setNodeExecutionId(nodeExecutionId);
        event.setEventType(eventType);
        event.setPayload(writeJson(payload));
        eventMapper.insert(event);
    }

    private WorkflowExecution findByIdempotencyKey(String triggerType, String triggerKey) {
        return executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getTriggerType, triggerType)
                .eq(WorkflowExecution::getTriggerKey, triggerKey));
    }

    private ExecutionGraph graphOf(WorkflowExecution execution) {
        List<NodeExecution> nodes = loadNodes(execution.getId());
        Set<String> keys = nodes.stream().map(NodeExecution::getNodeKey).collect(Collectors.toSet());
        return new ExecutionGraph(keys, readEdges(execution.getEdgesSnapshot()));
    }

    private Map<String, String> resolvedConditions(List<NodeExecution> nodes) {
        Map<String, String> resolved = new HashMap<>();
        for (NodeExecution node : nodes) {
            if (!NodeType.CONDITION.name().equals(node.getNodeType())) {
                continue;
            }
            if (!NodeStatus.SUCCESS.name().equals(node.getStatus())) {
                continue;
            }
            Object result = readJsonMap(node.getOutputJson()).get("result");
            if (result != null) {
                resolved.put(node.getNodeKey(), Boolean.parseBoolean(String.valueOf(result)) ? "TRUE" : "FALSE");
            }
        }
        return resolved;
    }

    private List<NodeExecution> loadNodes(Long executionId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<NodeExecution>()
                .eq(NodeExecution::getExecutionId, executionId)
                .orderByAsc(NodeExecution::getId));
    }

    private Map<String, NodeExecution> index(List<NodeExecution> nodes) {
        Map<String, NodeExecution> map = new LinkedHashMap<>();
        for (NodeExecution node : nodes) {
            map.put(node.getNodeKey(), node);
        }
        return map;
    }

    private Set<String> rootsOf(List<NodeSpec> nodes, List<EdgeSpec> edges) {
        Set<String> hasIncoming = edges.stream()
                .map(EdgeSpec::toNodeKey)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        return nodes.stream()
                .map(NodeSpec::nodeKey)
                .filter(key -> !hasIncoming.contains(key))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isCancellable(String status) {
        return NodeStatus.WAITING.name().equals(status)
                || NodeStatus.READY.name().equals(status)
                || NodeStatus.RETRY_WAIT.name().equals(status);
    }

    private List<ExecutionGraph.Edge> readEdges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<EdgeSpec> specs = objectMapper.readValue(json, new TypeReference<List<EdgeSpec>>() {
            });
            return specs.stream()
                    .filter(spec -> spec.fromNodeKey() != null && spec.toNodeKey() != null)
                    .map(spec -> new ExecutionGraph.Edge(spec.fromNodeKey(), spec.toNodeKey(), spec.conditionValue()))
                    .toList();
        } catch (JsonProcessingException ex) {
            log.warn("边快照解析失败，按空图处理", ex);
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }
}
