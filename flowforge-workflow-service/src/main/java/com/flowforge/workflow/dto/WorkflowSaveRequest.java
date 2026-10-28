package com.flowforge.workflow.dto;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建 / 保存草稿的请求。
 *
 * <p>这里只做字段级校验（长度、必填），DAG 的结构与拓扑校验由 {@code DagValidator} 负责，
 * 两者分开是为了让「保存草稿」可以放宽拓扑限制、而「发布」必须严格通过。
 */
public record WorkflowSaveRequest(

        @NotBlank(message = "工作流名称不能为空")
        @Size(max = 64, message = "工作流名称不能超过 64 个字符")
        String name,

        @Size(max = 512, message = "描述不能超过 512 个字符")
        String description,

        List<NodeSpec> nodes,

        List<EdgeSpec> edges
) {
}
