package com.flowforge.execution.messaging;

/**
 * 节点可执行消息。
 *
 * <p>统一信封：{@code eventId} 是消费端幂等的第一依据；
 * 消息体**不带**任何凭证、JWT 或完整业务载荷，只放定位记录所需的 ID。
 */
public record NodeReadyMessage(
        String eventId,
        String eventType,
        String occurredAt,
        String traceId,
        Long executionId,
        Long nodeExecutionId,
        Integer workflowVersion,
        String nodeKey
) {

    public static final String TYPE_NODE_READY = "NODE_READY";
}
