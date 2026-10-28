package com.flowforge.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 工作流终结事件消费者。
 *
 * <p>通知属于**尽力而为**的旁路能力：漏一条通知不会让执行结果出错，
 * 因此这里不做延迟重投，处理失败只记录日志——避免把非关键路径的故障
 * 放大成整个消息链路的阻塞。
 *
 * <p>真正不能妥协的是**幂等**：同一条事件重复投递时，
 * {@code notification_record.uk_source_event} 会挡住第二条通知。
 */
@Component
public class WorkflowFinishedConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowFinishedConsumer.class);

    private static final String QUEUE_WORKFLOW_FINISHED = "q.workflow.finished";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public WorkflowFinishedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QUEUE_WORKFLOW_FINISHED)
    public void onMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            WorkflowFinishedMessage event = objectMapper.readValue(payload, WorkflowFinishedMessage.class);
            notificationService.handleWorkflowFinished(event);
        } catch (JsonProcessingException ex) {
            log.error("工作流终结消息无法解析，丢弃 eventId 未知", ex);
        } catch (Exception ex) {
            log.error("处理工作流终结消息失败，丢弃该通知", ex);
        }

        acknowledge(channel, deliveryTag);
    }

    private void acknowledge(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ex) {
            log.warn("ACK 失败 deliveryTag={}", deliveryTag);
        }
    }
}
