package com.flowforge.execution.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 交换机与队列声明。
 *
 * <p>全部 durable：Broker 重启后队列与消息都还在。
 * 主队列配置死信出口，业务上的「最终失败」统一落到 {@code q.node.dead}，不会静默消失。
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange executionExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    // ---------------------------------------------------------------- 主队列

    @Bean
    public Queue nodeExecuteQueue() {
        return QueueBuilder.durable(RabbitTopology.QUEUE_NODE_EXECUTE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.RK_NODE_DEAD)
                .build();
    }

    @Bean
    public Binding nodeExecuteBinding() {
        return BindingBuilder.bind(nodeExecuteQueue())
                .to(executionExchange())
                .with(RabbitTopology.RK_NODE_READY);
    }

    @Bean
    public Queue workflowFinishedQueue() {
        return QueueBuilder.durable(RabbitTopology.QUEUE_WORKFLOW_FINISHED)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.RK_NODE_DEAD)
                .build();
    }

    @Bean
    public Binding workflowFinishedBinding() {
        return BindingBuilder.bind(workflowFinishedQueue())
                .to(executionExchange())
                .with(RabbitTopology.RK_WORKFLOW_FINISHED);
    }

    // ---------------------------------------------------------------- 死信

    @Bean
    public Queue nodeDeadQueue() {
        return QueueBuilder.durable(RabbitTopology.QUEUE_NODE_DEAD).build();
    }

    @Bean
    public Binding nodeDeadBinding() {
        return BindingBuilder.bind(nodeDeadQueue())
                .to(deadLetterExchange())
                .with(RabbitTopology.RK_NODE_DEAD);
    }

    // ---------------------------------------------------------------- 延迟重试

    /**
     * 延迟队列本身没有消费者：消息进队后静默等待 TTL 到期，
     * 到期后被死信回主交换机、按 {@code node.ready} 重新投递。
     */
    @Bean
    public Queue retry5sQueue() {
        return retryQueue("5s", 5_000);
    }

    @Bean
    public Queue retry30sQueue() {
        return retryQueue("30s", 30_000);
    }

    @Bean
    public Queue retry2mQueue() {
        return retryQueue("2m", 120_000);
    }

    private Queue retryQueue(String suffix, int ttlMs) {
        return QueueBuilder.durable(RabbitTopology.retryQueueName(suffix))
                .ttl(ttlMs)
                .deadLetterExchange(RabbitTopology.EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.RK_NODE_READY)
                .build();
    }

    @Bean
    public Binding retry5sBinding() {
        return BindingBuilder.bind(retry5sQueue()).to(executionExchange())
                .with(RabbitTopology.retryRoutingKey("5s"));
    }

    @Bean
    public Binding retry30sBinding() {
        return BindingBuilder.bind(retry30sQueue()).to(executionExchange())
                .with(RabbitTopology.retryRoutingKey("30s"));
    }

    @Bean
    public Binding retry2mBinding() {
        return BindingBuilder.bind(retry2mQueue()).to(executionExchange())
                .with(RabbitTopology.retryRoutingKey("2m"));
    }
}
