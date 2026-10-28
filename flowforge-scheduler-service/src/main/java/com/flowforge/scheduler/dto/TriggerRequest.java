package com.flowforge.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手动触发请求。
 *
 * @param requestId 由前端生成的幂等键。重复点击、网络重试只要携带同一个值，
 *                  就只会产生一个执行实例——服务端不猜用户的意图，只认这个键。
 */
public record TriggerRequest(

        @NotNull(message = "workflowId 不能为空")
        Long workflowId,

        @NotBlank(message = "requestId 不能为空")
        @Size(max = 128, message = "requestId 不能超过 128 个字符")
        String requestId,

        Integer timeoutSeconds
) {
}
