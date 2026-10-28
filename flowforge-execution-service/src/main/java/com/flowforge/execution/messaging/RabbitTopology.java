package com.flowforge.execution.messaging;

/**
 * RabbitMQ 拓扑常量。
 *
 * <p>延迟重试用「TTL 队列 + 死信回流」实现，而不是安装
 * {@code rabbitmq_delayed_message_exchange} 插件——换个环境就不用手工装插件，
 * 代价是延迟精度只到固定档位，对任务重试完全够用。
 */
public final class RabbitTopology {

    public static final String EXCHANGE = "flowforge.execution";
    public static final String DEAD_LETTER_EXCHANGE = "flowforge.dlx";

    /** 待执行节点 */
    public static final String QUEUE_NODE_EXECUTE = "q.node.execute";
    public static final String RK_NODE_READY = "node.ready";

    /** 工作流终态事件 */
    public static final String QUEUE_WORKFLOW_FINISHED = "q.workflow.finished";
    public static final String RK_WORKFLOW_FINISHED = "workflow.finished";

    /** 最终失败的死信 */
    public static final String QUEUE_NODE_DEAD = "q.node.dead";
    public static final String RK_NODE_DEAD = "node.dead";

    /** 延迟档位：5 秒 / 30 秒 / 2 分钟 */
    public static final String[] RETRY_SUFFIXES = {"5s", "30s", "2m"};
    public static final int[] RETRY_TTLS_MS = {5_000, 30_000, 120_000};

    public static String retryQueueName(String suffix) {
        return "q.node.retry." + suffix;
    }

    public static String retryRoutingKey(String suffix) {
        return "node.retry." + suffix;
    }

    /** 按重试次数挑选延迟档位，超出档位数量时落在最长的档位。 */
    public static int ttlForAttempt(int retryCount) {
        int index = Math.min(Math.max(retryCount, 0), RETRY_TTLS_MS.length - 1);
        return RETRY_TTLS_MS[index];
    }

    public static String suffixForAttempt(int retryCount) {
        int index = Math.min(Math.max(retryCount, 0), RETRY_SUFFIXES.length - 1);
        return RETRY_SUFFIXES[index];
    }

    private RabbitTopology() {
    }
}
