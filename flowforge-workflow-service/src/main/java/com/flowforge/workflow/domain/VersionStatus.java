package com.flowforge.workflow.domain;

/**
 * 版本状态。
 *
 * <p>每个工作流永远存在一条 {@code version_no = 0} 的 DRAFT 版本承载草稿；
 * 发布时它转成 PUBLISHED 并复制出一份新的 DRAFT，实现「版本不可变」。
 */
public enum VersionStatus {

    /** 草稿，可修改 */
    DRAFT,

    /** 已发布，禁止修改，执行实例引用它 */
    PUBLISHED
}
