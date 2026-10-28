package com.flowforge.execution.messaging;

import com.flowforge.common.util.SensitiveDataMasker;
import com.flowforge.execution.entity.DeadLetterRecord;
import com.flowforge.execution.mapper.DeadLetterRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 死信队列消费者。
 *
 * <p>进入这里的消息不会静默消失：统一落一条 {@code dead_letter_record}，
 * 用户在「执行详情」里能查到「消息处理失败」，运维也能按 eventId 定位。
 *
 * <p>第一版不做自动重放——重放要保证沿用原 eventId，否则消费端幂等会被绕过，
 * 这件事必须人工判断，不能自动做。
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final int MAX_PAYLOAD_RECORD = 2_000;

    private final DeadLetterRecordMapper deadLetterRecordMapper;

    public DeadLetterConsumer(DeadLetterRecordMapper deadLetterRecordMapper) {
        this.deadLetterRecordMapper = deadLetterRecordMapper;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_NODE_DEAD)
    public void onDeadLetter(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = message.getMessageProperties().getHeaders();

        DeadLetterRecord record = new DeadLetterRecord();
        record.setEventId(UUID.randomUUID().toString());
        record.setEventType("NODE_READY_DEAD");
        record.setSourceQueue(RabbitTopology.QUEUE_NODE_DEAD);
        record.setPayload(SensitiveDataMasker.maskAndTruncate(payload, MAX_PAYLOAD_RECORD));
        record.setReason("消息进入死信队列，原因：" + headers.getOrDefault("x-first-death-reason", "unknown"));
        record.setStatus("PENDING");

        try {
            deadLetterRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.debug("死信记录已存在，忽略");
        } catch (Exception ex) {
            log.error("写入死信记录失败", ex);
        }
        log.warn("收到死信消息，已记录 deadLetterId={}", record.getEventId());
    }
}
