package com.flowforge.common.contract;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;

import java.util.List;

/**
 * 已发布版本的不可变快照，供调度服务创建执行实例时读取。
 *
 * <p>**这是跨服务的只读契约**：执行实例创建后不再回查工作流服务，
 * 后续编辑与重新发布都不会影响已经在跑的实例。
 */
public record WorkflowSnapshot(
        Long workflowId,
        Long versionId,
        Integer versionNo,
        Long ownerId,
        String name,
        String status,
        List<NodeSpec> nodes,
        List<EdgeSpec> edges
) {
}
