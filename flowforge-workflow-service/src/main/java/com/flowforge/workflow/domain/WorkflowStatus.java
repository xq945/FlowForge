package com.flowforge.workflow.domain;

/**
 * 工作流定义状态。只有 DRAFT 允许编辑节点与依赖。
 */
public enum WorkflowStatus {

    /** 草稿：可编辑，不可执行 */
    DRAFT,

    /** 已发布：存在可执行的版本快照 */
    PUBLISHED,

    /** 已停用：不影响在途实例，只拒绝新的触发 */
    DISABLED
}
