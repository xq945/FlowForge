package com.flowforge.common.contract;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 服务间创建实例的请求。
 *
 * <p>快照随请求一起传入，执行服务不会再去回查工作流服务——
 * 实例一旦创建就与定义解耦，后续编辑、重新发布、甚至删除工作流都不影响它。
 */
public record ExecutionCreateRequest(

        @NotNull(message = "workflowId 不能为空")
        Long workflowId,

        @NotNull(message = "workflowVersionId 不能为空")
        Long workflowVersionId,

        @NotNull(message = "versionNo 不能为空")
        Integer versionNo,

        @NotNull(message = "ownerId 不能为空")
        Long ownerId,

        @NotBlank(message = "triggerType 不能为空")
        String triggerType,

        @NotBlank(message = "triggerKey 不能为空")
        String triggerKey,

        Integer timeoutSeconds,

        @NotEmpty(message = "节点快照不能为空")
        List<NodeSpec> nodes,

        List<EdgeSpec> edges
) {
}
