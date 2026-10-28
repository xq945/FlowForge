package com.flowforge.execution.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.util.SensitiveDataMasker;
import com.flowforge.execution.engine.ExecutionEngine;
import com.flowforge.execution.entity.DeadLetterRecord;
import com.flowforge.execution.mapper.DeadLetterRecordMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 节点执行消费者。
 *
 * <p>三条约束：
 * <ol>
 *   <li><b>处理完再 ACK</b>：先 ACK 再执行，进程崩溃就永久丢消息；</li>
 *   <li><b>不做原地重试</b>：异常时把消息投递到延迟队列再 ACK，避免同一条消息在热门队列里打转；</li>
 *   <li><b>毒消息直接丢弃并留痕</b>：反序列化失败的消息重投多少次都不会变好。</li>
 * </ol>
 *
 * <p>幂等由 {@code ExecutionEngine.runNode} 的条件更新保证——重复投递只会被静默丢弃。
 */
@Component
public class NodeReadyConsumer {

    private static final Logger log = LoggerFactory.getLogger(NodeReadyConsumer.class);

    private static final int MAX_MESSAGE_RETRY = 3;
    private static final String HEADER_RETRY_COUNT = "x-retry-count";
    private static final int MAX_PAYLOAD_RECORD = 2_000;

    private final ExecutionEngine executionEngine;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DeadLetterRecordMapper deadLetterRecordMapper;

    public NodeReadyConsumer(ExecutionEngine executionEngine,
                             ObjectMapper objectMapper,
                             RabbitTemplate rabbitTemplate,
                             DeadLetterRecordMapper deadLetterRecordMapper) {
        this.executionEngine = executionEngine;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.deadLetterRecordMapper = deadLetterRecordMapper;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_NODE_EXECUTE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        NodeReadyMessage event;
        try {
            event = objectMapper.readValue(payload, NodeReadyMessage.class);
        } catch (JsonProcessingException ex) {
            log.error("节点消息无法解析，直接丢弃并留痕", ex);
            recordDeadLetter(payload, null, "消息格式错误：" + ex.getOriginalMessage());
            acknowledge(channel, deliveryTag);
            return;
        }

        try {
            executionEngine.runNode(event.nodeExecutionId());
            acknowledge(channel, deliveryTag);
        } catch (Exception ex) {
            handleFailure(message, channel, deliveryTag, event, payload, ex);
        }
    }

    private void handleFailure(Message message, Channel channel, long deliveryTag,
                               NodeReadyMessage event, String payload, Exception ex) throws IOException {
        Integer header = (Integer) message.getMessageProperties().getHeaders().get(HEADER_RETRY_COUNT);
        int attempt = header == null ? 1 : header + 1;

        if (attempt > MAX_MESSAGE_RETRY) {
            rabbitTemplate.convertAndSend(RabbitTopology.DEAD_LETTER_EXCHANGE, RabbitTopology.RK_NODE_DEAD,
                    payload, m -> {
                        m.getMessageProperties().setHeader(HEADER_RETRY_COUNT, attempt);
                        return m;
                    });
            recordDeadLetter(payload, event, "消费重试 " + MAX_MESSAGE_RETRY + " 次仍失败：" + ex.getMessage());
            acknowledge(channel, deliveryTag);
            log.error("节点消息进入死信 executionId={} nodeExecutionId={}",
                    event.executionId(), event.nodeExecutionId(), ex);
            return;
        }

        // 投递到延迟队列，TTL 到期后自动回流到主队列
        String suffix = RabbitTopology.suffixForAttempt(attempt - 1);
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.retryRoutingKey(suffix),
                payload, m -> {
                    m.getMessageProperties().setHeader(HEADER_RETRY_COUNT, attempt);
                    return m;
                });
        acknowledge(channel, deliveryTag);
        log.warn("节点消息处理失败，{} 后重试（第 {} 次）executionId={} nodeExecutionId={} reason={}",
                suffix, attempt, event.executionId(), event.nodeExecutionId(), ex.getMessage());
    }

    /**
     * ACK 本身也可能失败（连接已断）。此时消息会被 Broker 重新投递，
     * 由条件更新兜底——不要因为 ACK 失败就让消费线程抛出异常。
     */
    private void acknowledge(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ex) {
            log.warn("ACK 失败，消息将被重新投递 deliveryTag={}", deliveryTag);
        }
    }

    private void recordDeadLetter(String payload, NodeReadyMessage event, String reason) {
        DeadLetterRecord record = new DeadLetterRecord();
        record.setEventId(event != null && event.eventId() != null
                ? truncate(event.eventId(), 36) : UUID.randomUUID().toString());
        record.setEventType(event != null ? event.eventType() : "UNKNOWN");
        record.setSourceQueue(RabbitTopology.QUEUE_NODE_EXECUTE);
        record.setExecutionId(event != null ? event.executionId() : null);
        record.setPayload(SensitiveDataMasker.maskAndTruncate(payload, MAX_PAYLOAD_RECORD));
        record.setReason(truncate(reason, 500));
        record.setStatus("PENDING");
        try {
            deadLetterRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.debug("死信记录已存在，忽略 eventId={}", record.getEventId());
        } catch (Exception ex) {
            log.error("写入死信记录失败", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
