package com.flowforge.scheduler.client;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.contract.ExecutionCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 执行服务的内部接口。
 *
 * <p>创建实例的请求里带上完整快照，执行服务不需要再回查工作流服务，
 * 服务之间的同步调用链到此为止。
 */
@FeignClient(name = "flowforge-execution-service", path = "/internal/executions",
        configuration = FeignAuthConfig.class)
public interface ExecutionClient {

    @PostMapping
    ApiResponse<Map<String, Long>> create(@RequestBody ExecutionCreateRequest request);
}
