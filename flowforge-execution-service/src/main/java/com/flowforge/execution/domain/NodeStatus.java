package com.flowforge.execution.domain;

/**
 * 节点执行状态。
 *
 * <p>合法迁移见 docs/03-业务流程与状态机.md 第 4 节；
 * 代码里只能通过带前置状态条件的 UPDATE 完成迁移，不允许「先查后改」。
 */
public enum NodeStatus {

    /** 等待前置节点完成 */
    WAITING,

    /** 前置已满足，等待被抢占执行 */
    READY,

    /** 正在执行 */
    RUNNING,

    /** 失败后等待退避到期 */
    RETRY_WAIT,

    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == CANCELLED;
    }

    /** 终态中表示「顺利走完」的两种，下游可以据此判断自己是否可以开始。 */
    public boolean isSuccessfulTerminal() {
        return this == SUCCESS || this == SKIPPED;
    }
}
