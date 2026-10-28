package com.flowforge.execution.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.api.TraceContext;
import com.flowforge.execution.entity.OutboxEvent;
import com.flowforge.execution.mapper.OutboxEventMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 事务发件箱写入。
 *
 * <p>**必须在业务事务内调用**：与节点状态变更一起提交，
 * 保证「状态落库 ⇔ 消息最终一定发出」。消息发送由 {@link OutboxPublisher} 异步完成。
 */
@Component
public class OutboxWriter {

    private static final String STATUS_PENDING = "PENDING";

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventMapper outboxEventMapper, ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    /** 写入一条「节点可执行」事件。 */
    public String appendNodeReady(Long executionId, Long nodeExecutionId,
                                  Integer workflowVersion, String nodeKey) {
        String eventId = UUID.randomUUID().toString();
        NodeReadyMessage message = new NodeReadyMessage(
                eventId,
                NodeReadyMessage.TYPE_NODE_READY,
                Instant.now().toString(),
                TraceContext.currentTraceId(),
                executionId,
                nodeExecutionId,
                workflowVersion,
                nodeKey);

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType(NodeReadyMessage.TYPE_NODE_READY);
        event.setAggregateType("NODE");
        event.setAggregateId(String.valueOf(nodeExecutionId));
        event.setPayload(writeJson(message));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        outboxEventMapper.insert(event);
        return eventId;
    }

    /** 写入一条「工作流终结」事件，供通知服务消费。 */
    public String appendWorkflowFinished(Long executionId, Long ownerId, String status,
                                         String errorCode, String errorMessage) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "eventType", "WORKFLOW_FINISHED",
                "occurredAt", Instant.now().toString(),
                "executionId", executionId,
                "ownerId", ownerId,
                "status", status,
                "errorCode", errorCode == null ? "" : errorCode,
                "errorMessage", errorMessage == null ? "" : errorMessage);

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType("WORKFLOW_FINISHED");
        event.setAggregateType("EXECUTION");
        event.setAggregateId(String.valueOf(executionId));
        event.setPayload(writeJson(payload));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        outboxEventMapper.insert(event);
        return eventId;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox 事件序列化失败", ex);
        }
    }
}
