package com.flowforge.execution.domain;

/**
 * 触发来源。与 triggerKey 组合成幂等唯一键。
 */
public enum TriggerType {

    /** 用户手动点击，triggerKey 为前端生成的 requestId */
    MANUAL,

    /** 周期任务，triggerKey 为 {@code jobId:fireTime} */
    SCHEDULE,

    /** 对失败节点的手动重跑，triggerKey 为新生成的 requestId */
    MANUAL_RETRY
}
