package com.flowforge.execution.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 实例超时扫描器。
 *
 * <p>「谁来判断超时」这件事必须落在数据库上：内存里的定时器在进程崩溃后就没了，
 * 而 {@code workflow_execution.deadline_at} 是持久化的，重启后还能继续判定。
 *
 * <p>多实例安全：真正的状态迁移在行锁内完成，且只对 {@code status='RUNNING'} 生效。
 */
@Component
public class ExecutionTimeoutScanner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeoutScanner.class);
    private static final int BATCH_SIZE = 50;

    private final ExecutionEngine executionEngine;

    public ExecutionTimeoutScanner(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
    }

    @Scheduled(fixedDelayString = "${flowforge.execution.timeout-scan-interval-ms:5000}")
    public void scan() {
        try {
            int expired = executionEngine.timeoutExpired(BATCH_SIZE);
            if (expired > 0) {
                log.info("本次扫描终结了 {} 个超时实例", expired);
            }
        } catch (Exception ex) {
            log.error("超时扫描失败", ex);
        }
    }
}
