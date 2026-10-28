package com.flowforge.execution.engine;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;

import java.util.List;

/**
 * 创建执行实例的入参。
 *
 * <p>由调度服务提供：它先从工作流服务取到版本快照，再把快照整体传进来。
 * 这样执行服务运行时不依赖工作流服务，实例一旦创建就是自包含的。
 */
public record CreateExecutionCommand(
        Long workflowId,
        Long workflowVersionId,
        Integer versionNo,
        Long ownerId,
        String triggerType,
        String triggerKey,
        List<NodeSpec> nodes,
        List<EdgeSpec> edges,
        Integer timeoutSeconds
) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 1800;
}
