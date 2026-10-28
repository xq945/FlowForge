package com.flowforge.workflow.dto;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流详情：默认返回**草稿**内容（可编辑），版本历史单独列出。
 */
public record WorkflowDetail(
        Long id,
        String name,
        String description,
        String status,
        Long currentVersionId,
        Integer latestVersionNo,
        Long draftVersionId,
        List<NodeSpec> nodes,
        List<EdgeSpec> edges,
        List<VersionInfo> versions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
