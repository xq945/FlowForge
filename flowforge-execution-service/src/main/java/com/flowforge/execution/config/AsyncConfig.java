package com.flowforge.execution.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 节点执行线程池。
 *
 * <p>阶段三用本地线程池驱动节点；阶段四换成 RabbitMQ 消费时，
 * 这个池会变成消费者内部的执行器，状态机与推进逻辑不变。
 *
 * <p>拒绝策略用 CallerRunsPolicy：队列打满时由提交线程自己执行，
 * 宁可拖慢调度，也不要把节点任务悄悄丢掉。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "nodeExecutorPool")
    public ThreadPoolTaskExecutor nodeExecutorPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("node-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
