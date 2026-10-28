package com.flowforge.scheduler.client;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.contract.WorkflowSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作流服务的内部接口。
 *
 * <p>只做**读**：取版本快照。调度服务不写工作流库，也不参与工作流编辑。
 */
@FeignClient(name = "flowforge-workflow-service", path = "/internal/workflows")
public interface WorkflowClient {

    @GetMapping("/{workflowId}/snapshot")
    ApiResponse<WorkflowSnapshot> snapshot(@PathVariable("workflowId") Long workflowId,
                                           @RequestParam(value = "versionId", required = false) Long versionId);
}
