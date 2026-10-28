package com.flowforge.notification.messaging;

/**
 * 工作流终结事件。
 *
 * <p>由执行服务的 Outbox 发出，字段与 {@code OutboxWriter#appendWorkflowFinished} 保持一致。
 */
public record WorkflowFinishedMessage(
        String eventId,
        String eventType,
        String occurredAt,
        Long executionId,
        Long ownerId,
        String status,
        String errorCode,
        String errorMessage
) {
}
