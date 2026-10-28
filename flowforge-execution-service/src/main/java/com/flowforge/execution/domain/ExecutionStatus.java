package com.flowforge.execution.domain;

/**
 * 工作流执行实例状态。
 *
 * <p>终态不可逆：手动重试失败节点是创建**新实例**，而不是把旧实例拉回 RUNNING。
 */
public enum ExecutionStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }
}
