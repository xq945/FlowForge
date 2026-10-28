package com.flowforge.execution.messaging;

import com.flowforge.execution.entity.OutboxEvent;
import com.flowforge.execution.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 发件箱发布器。
 *
 * <p>每 200ms 扫一批 {@code PENDING} 事件，逐条发布并**同步等待发布确认**：
 * 只有 Broker 明确 ack 且消息没有 return（无法路由），才标记为 SENT。
 *
 * <p>崩溃场景全部落在「事件仍是 PENDING」上：
 * 事务提交后进程挂掉、发布成功但没来得及改状态——下一轮扫描都会重发，
 * 代价是可能重复投递一次，由消费端幂等吸收。**宁可重复，不可丢失。**
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 10;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventMapper outboxEventMapper, RabbitTemplate rabbitTemplate) {
        this.outboxEventMapper = outboxEventMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${flowforge.outbox.publish-interval-ms:200}")
    public void publishPending() {
        List<OutboxEvent> batch;
        try {
            batch = outboxEventMapper.selectPending(BATCH_SIZE);
        } catch (Exception ex) {
            log.error("扫描 Outbox 失败", ex);
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                publishOne(event);
            } catch (Exception ex) {
                // 单条失败不影响本批其它事件
                handleFailure(event, ex);
            }
        }
    }

    private void publishOne(OutboxEvent event) throws Exception {
        CorrelationData correlation = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, routingKeyOf(event),
                event.getPayload(), correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("Broker 拒绝了消息：" + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            // Confirm 成功但消息无法路由，说明队列/绑定缺失，属于配置问题，必须暴露出来
            throw new IllegalStateException("消息无法路由：" + correlation.getReturned().getReplyText());
        }
        outboxEventMapper.markSent(event.getId());
    }

    private String routingKeyOf(OutboxEvent event) {
        return switch (event.getEventType()) {
            case NodeReadyMessage.TYPE_NODE_READY -> RabbitTopology.RK_NODE_READY;
            case "WORKFLOW_FINISHED" -> RabbitTopology.RK_WORKFLOW_FINISHED;
            default -> throw new IllegalStateException("未知事件类型：" + event.getEventType());
        };
    }

    private void handleFailure(OutboxEvent event, Exception ex) {
        int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        String error = truncate(ex.getMessage());

        if (retryCount >= MAX_RETRY) {
            outboxEventMapper.markFailed(event.getId(), error);
            log.error("Outbox 事件发布失败次数超限，转入 FAILED eventId={} type={}",
                    event.getEventId(), event.getEventType(), ex);
            return;
        }

        long delayMs = Math.min(60_000, 500L << Math.min(retryCount, 7));
        outboxEventMapper.markRetry(event.getId(),
                LocalDateTime.now().plusNanos(delayMs * 1_000_000), error);
        log.warn("Outbox 事件发布失败，{}ms 后重试 eventId={} retry={} reason={}",
                delayMs, event.getEventId(), retryCount, error);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
