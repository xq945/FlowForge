package com.flowforge.execution.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 退避到期的节点扫描器。
 *
 * <p>阶段三由它把 {@code RETRY_WAIT} 的节点放回 {@code READY}；阶段四接入延迟队列后，
 * 消息负责「尽快重试」，这个扫描器继续保留作为**兜底**——消息丢了它还能把状态捡回来。
 *
 * <p>多实例安全：放行用的是带 {@code status='RETRY_WAIT'} 条件的 UPDATE，
 * 多个实例同时扫到同一条也只有一个能改成功。
 */
@Component
public class RetryScanner {

    private static final Logger log = LoggerFactory.getLogger(RetryScanner.class);
    private static final int BATCH_SIZE = 50;

    private final ExecutionEngine executionEngine;

    public RetryScanner(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
    }

    @Scheduled(fixedDelayString = "${flowforge.execution.retry-scan-interval-ms:1000}")
    public void scanDueRetries() {
        try {
            executionEngine.releaseDueRetries(BATCH_SIZE);
        } catch (Exception ex) {
            // 扫描器本身不能因为一次异常就停掉；记录后等下一轮
            log.error("重试扫描失败", ex);
        }
    }
}
