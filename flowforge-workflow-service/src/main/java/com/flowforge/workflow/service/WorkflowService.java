package com.flowforge.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import com.flowforge.workflow.domain.ValidationResult;
import com.flowforge.workflow.domain.VersionStatus;
import com.flowforge.workflow.domain.WorkflowStatus;
import com.flowforge.workflow.dto.PublishResult;
import com.flowforge.workflow.dto.VersionInfo;
import com.flowforge.workflow.dto.WorkflowDetail;
import com.flowforge.workflow.dto.WorkflowSaveRequest;
import com.flowforge.common.contract.WorkflowSnapshot;
import com.flowforge.workflow.dto.WorkflowSummary;
import com.flowforge.workflow.entity.WorkflowDefinition;
import com.flowforge.workflow.entity.WorkflowEdge;
import com.flowforge.workflow.entity.WorkflowNode;
import com.flowforge.workflow.entity.WorkflowVersion;
import com.flowforge.workflow.mapper.WorkflowDefinitionMapper;
import com.flowforge.workflow.mapper.WorkflowEdgeMapper;
import com.flowforge.workflow.mapper.WorkflowNodeMapper;
import com.flowforge.workflow.mapper.WorkflowVersionMapper;
import com.flowforge.workflow.validation.DagValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    /** 草稿固定用 0 号版本承载，发布时才升为 ≥1 */
    private static final int DRAFT_VERSION_NO = 0;
    private static final int MAX_CONFIG_LENGTH = 16_384;

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final DagValidator dagValidator;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowDefinitionMapper definitionMapper,
                           WorkflowVersionMapper versionMapper,
                           WorkflowNodeMapper nodeMapper,
                           WorkflowEdgeMapper edgeMapper,
                           DagValidator dagValidator,
                           ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.dagValidator = dagValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowDetail create(Long ownerId, WorkflowSaveRequest request) {
        // 草稿允许不完整：只校验结构与配置，拓扑问题留到发布时再拦
        requireValid(request, false);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setOwnerId(ownerId);
        definition.setName(request.name().trim());
        definition.setDescription(request.description());
        definition.setStatus(WorkflowStatus.DRAFT.name());
        definition.setLatestVersionNo(0);
        try {
            definitionMapper.insert(definition);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.WORKFLOW_NAME_TAKEN);
        }

        WorkflowVersion draft = newDraftVersion(definition.getId());
        writeGraph(draft.getId(), request.nodes(), request.edges());

        log.info("工作流创建成功 workflowId={} ownerId={}", definition.getId(), ownerId);
        return detail(ownerId, definition.getId());
    }

    public PageResult<WorkflowSummary> list(Long ownerId, String keyword, long page, long size) {
        Page<WorkflowDefinition> query = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<WorkflowDefinition>()
                .eq(WorkflowDefinition::getOwnerId, ownerId)
                .like(StringUtils.hasText(keyword), WorkflowDefinition::getName, keyword)
                .orderByDesc(WorkflowDefinition::getUpdatedAt);
        Page<WorkflowDefinition> result = definitionMapper.selectPage(query, wrapper);

        List<WorkflowSummary> records = result.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(records, result.getTotal(), query.getCurrent(), query.getSize());
    }

    public WorkflowDetail detail(Long ownerId, Long workflowId) {
        WorkflowDefinition definition = requireOwned(workflowId, ownerId);
        WorkflowVersion draft = findDraftVersion(workflowId);

        List<NodeSpec> nodes = draft == null ? List.of() : readNodes(draft.getId());
        List<EdgeSpec> edges = draft == null ? List.of() : readEdges(draft.getId());
        List<VersionInfo> versions = versionMapper.selectAllByWorkflow(workflowId).stream()
                .filter(version -> version.getVersionNo() > DRAFT_VERSION_NO)
                .map(version -> new VersionInfo(version.getId(), version.getVersionNo(), version.getStatus(),
                        version.getContentHash(), version.getPublishedAt()))
                .toList();

        return new WorkflowDetail(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getStatus(),
                definition.getCurrentVersionId(),
                definition.getLatestVersionNo(),
                draft == null ? null : draft.getId(),
                nodes,
                edges,
                versions,
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    @Transactional
    public WorkflowDetail saveDraft(Long ownerId, Long workflowId, WorkflowSaveRequest request) {
        WorkflowDefinition definition = requireOwned(workflowId, ownerId);
        requireValid(request, false);

        WorkflowVersion draft = findDraftVersion(workflowId);
        if (draft == null) {
            draft = newDraftVersion(workflowId);
        }
        writeGraph(draft.getId(), request.nodes(), request.edges());

        definition.setName(request.name().trim());
        definition.setDescription(request.description());
        try {
            definitionMapper.updateById(definition);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.WORKFLOW_NAME_TAKEN);
        }

        return detail(ownerId, workflowId);
    }

    /**
     * 发布：把当前草稿固化为不可变版本，并复制出一份新的空草稿供继续编辑。
     *
     * <p>整个流程在事务内完成，并用 {@code SELECT ... FOR UPDATE} 锁住定义行，
     * 避免并发发布产生两个相同的版本号。
     */
    @Transactional
    public PublishResult publish(Long ownerId, Long workflowId) {
        WorkflowDefinition definition = definitionMapper.selectForUpdate(workflowId);
        if (definition == null || !definition.getOwnerId().equals(ownerId)) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }

        WorkflowVersion draft = findDraftVersion(workflowId);
        if (draft == null) {
            throw new BizException(ErrorCode.WORKFLOW_STATE_CONFLICT, "没有可发布的草稿");
        }

        List<NodeSpec> nodes = readNodes(draft.getId());
        List<EdgeSpec> edges = readEdges(draft.getId());
        ValidationResult validation = dagValidator.validate(nodes, edges, true);
        if (!validation.valid()) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID, String.join("；", validation.errors()));
        }

        int newVersionNo = definition.getLatestVersionNo() == null ? 1 : definition.getLatestVersionNo() + 1;
        String hash = contentHash(nodes, edges);
        LocalDateTime now = LocalDateTime.now();

        // 1. 草稿原地转为发布版本（version_no 由 0 改为新版本号）
        draft.setVersionNo(newVersionNo);
        draft.setStatus(VersionStatus.PUBLISHED.name());
        draft.setContentHash(hash);
        draft.setPublishedAt(now);
        draft.setPublishedBy(ownerId);
        versionMapper.updateById(draft);

        // 2. 复制一份新草稿，保证发布后仍能继续编辑
        WorkflowVersion nextDraft = newDraftVersion(workflowId);
        writeGraph(nextDraft.getId(), nodes, edges);

        // 3. 更新定义指向新发布的版本
        definition.setStatus(WorkflowStatus.PUBLISHED.name());
        definition.setCurrentVersionId(draft.getId());
        definition.setLatestVersionNo(newVersionNo);
        definitionMapper.updateById(definition);

        log.info("工作流发布成功 workflowId={} versionNo={} versionId={}",
                workflowId, newVersionNo, draft.getId());
        return new PublishResult(draft.getId(), newVersionNo, hash, now);
    }

    /**
     * 启用 / 停用。
     *
     * <p>停用只影响**新的触发**，已经在跑的实例仍按绑定的版本继续执行。
     */
    @Transactional
    public void changeStatus(Long ownerId, Long workflowId, WorkflowStatus target) {
        WorkflowDefinition definition = definitionMapper.selectForUpdate(workflowId);
        if (definition == null || !definition.getOwnerId().equals(ownerId)) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }

        String current = definition.getStatus();
        switch (target) {
            case DISABLED -> {
                if (!WorkflowStatus.PUBLISHED.name().equals(current)) {
                    throw new BizException(ErrorCode.WORKFLOW_STATE_CONFLICT, "只有已发布的工作流可以停用");
                }
                definition.setStatus(WorkflowStatus.DISABLED.name());
            }
            case PUBLISHED -> {
                if (definition.getCurrentVersionId() == null) {
                    throw new BizException(ErrorCode.WORKFLOW_NOT_PUBLISHED);
                }
                if (!WorkflowStatus.DISABLED.name().equals(current)
                        && !WorkflowStatus.PUBLISHED.name().equals(current)) {
                    throw new BizException(ErrorCode.WORKFLOW_STATE_CONFLICT, "草稿状态的工作流请先发布");
                }
                definition.setStatus(WorkflowStatus.PUBLISHED.name());
            }
            default -> throw new BizException(ErrorCode.WORKFLOW_STATE_CONFLICT, "不支持切换到该状态");
        }
        definitionMapper.updateById(definition);
        log.info("工作流状态变更 workflowId={} {} -> {}", workflowId, current, target);
    }

    /**
     * 取版本快照。{@code versionId} 为空时返回当前发布版本。
     *
     * <p>这是给调度服务的内部契约，不做归属校验——归属由调用方在用户请求入口处校验。
     */
    public WorkflowSnapshot snapshot(Long workflowId, Long versionId) {
        WorkflowDefinition definition = definitionMapper.selectById(workflowId);
        if (definition == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }

        Long targetVersionId = versionId != null ? versionId : definition.getCurrentVersionId();
        if (targetVersionId == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_PUBLISHED);
        }
        WorkflowVersion version = versionMapper.selectById(targetVersionId);
        if (version == null || !version.getWorkflowId().equals(workflowId)) {
            throw new BizException(ErrorCode.VERSION_NOT_FOUND);
        }

        return new WorkflowSnapshot(
                workflowId,
                version.getId(),
                version.getVersionNo(),
                definition.getOwnerId(),
                definition.getName(),
                definition.getStatus(),
                readNodes(version.getId()),
                readEdges(version.getId()));
    }

    // ---------------------------------------------------------------- 内部方法

    private WorkflowDefinition requireOwned(Long workflowId, Long ownerId) {
        WorkflowDefinition definition = definitionMapper.selectById(workflowId);
        // 不区分「不存在」与「不是你的」：避免通过错误码探测他人工作流是否存在
        if (definition == null || !definition.getOwnerId().equals(ownerId)) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        }
        return definition;
    }

    private void requireValid(WorkflowSaveRequest request, boolean strict) {
        ValidationResult result = dagValidator.validate(request.nodes(), request.edges(), strict);
        if (!result.valid()) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID, String.join("；", result.errors()));
        }
    }

    private WorkflowVersion findDraftVersion(Long workflowId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, workflowId)
                .eq(WorkflowVersion::getVersionNo, DRAFT_VERSION_NO));
    }

    private WorkflowVersion newDraftVersion(Long workflowId) {
        WorkflowVersion draft = new WorkflowVersion();
        draft.setWorkflowId(workflowId);
        draft.setVersionNo(DRAFT_VERSION_NO);
        draft.setStatus(VersionStatus.DRAFT.name());
        versionMapper.insert(draft);
        return versionMapper.selectById(draft.getId());
    }

    /** 全量覆盖：删掉再插，避免逐条 diff 带来的状态不一致。 */
    private void writeGraph(Long versionId, List<NodeSpec> nodes, List<EdgeSpec> edges) {
        nodeMapper.delete(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, versionId));
        edgeMapper.delete(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, versionId));

        if (nodes != null) {
            for (NodeSpec spec : nodes) {
                WorkflowNode node = new WorkflowNode();
                node.setWorkflowVersionId(versionId);
                node.setNodeKey(spec.nodeKey());
                node.setNodeType(spec.nodeType().toUpperCase());
                node.setName(spec.name() == null || spec.name().isBlank() ? spec.nodeKey() : spec.name());
                node.setConfig(serializeConfig(spec.config()));
                node.setTimeoutMs(spec.timeoutOrDefault());
                node.setRetryMax(spec.retryMaxOrDefault());
                node.setRetryInitialMs(spec.retryInitialOrDefault());
                node.setRetryMaxMs(spec.retryMaxDelayOrDefault());
                node.setRetryOnUncertain(spec.retryOnUncertainOrDefault() ? 1 : 0);
                node.setPosX(spec.posX() == null ? 0 : spec.posX());
                node.setPosY(spec.posY() == null ? 0 : spec.posY());
                nodeMapper.insert(node);
            }
        }

        if (edges != null) {
            for (EdgeSpec spec : edges) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setWorkflowVersionId(versionId);
                edge.setFromNodeKey(spec.fromNodeKey());
                edge.setToNodeKey(spec.toNodeKey());
                edge.setConditionValue(spec.conditionValue() == null ? null : spec.conditionValue().toUpperCase());
                edgeMapper.insert(edge);
            }
        }
    }

    private List<NodeSpec> readNodes(Long versionId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, versionId)
                        .orderByAsc(WorkflowNode::getId))
                .stream()
                .map(node -> new NodeSpec(
                        node.getNodeKey(),
                        node.getNodeType(),
                        node.getName(),
                        parseConfig(node.getConfig()),
                        node.getTimeoutMs(),
                        node.getRetryMax(),
                        node.getRetryInitialMs(),
                        node.getRetryMaxMs(),
                        node.getRetryOnUncertain() != null && node.getRetryOnUncertain() == 1,
                        node.getPosX(),
                        node.getPosY()))
                .toList();
    }

    private List<EdgeSpec> readEdges(Long versionId) {
        return edgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowVersionId, versionId)
                        .orderByAsc(WorkflowEdge::getId))
                .stream()
                .map(edge -> new EdgeSpec(edge.getFromNodeKey(), edge.getToNodeKey(), edge.getConditionValue()))
                .toList();
    }

    private String serializeConfig(Map<String, Object> config) {
        try {
            String json = objectMapper.writeValueAsString(config == null ? Map.of() : config);
            if (json.length() > MAX_CONFIG_LENGTH) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID, "单个节点的配置不能超过 " + MAX_CONFIG_LENGTH + " 字符");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID, "节点配置无法序列化为 JSON");
        }
    }

    private Map<String, Object> parseConfig(String json) {
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

    /** 内容指纹：节点与边按稳定顺序排序后再求 SHA-256，用于提示「内容未变更」。 */
    private String contentHash(List<NodeSpec> nodes, List<EdgeSpec> edges) {
        List<NodeSpec> sortedNodes = nodes.stream()
                .sorted(Comparator.comparing(NodeSpec::nodeKey))
                .toList();
        List<EdgeSpec> sortedEdges = edges.stream()
                .sorted(Comparator.comparing(EdgeSpec::fromNodeKey).thenComparing(EdgeSpec::toNodeKey))
                .toList();
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of("nodes", sortedNodes, "edges", sortedEdges));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("计算内容指纹失败", ex);
        }
    }

    private WorkflowSummary toSummary(WorkflowDefinition definition) {
        return new WorkflowSummary(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getStatus(),
                definition.getLatestVersionNo(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }
}
