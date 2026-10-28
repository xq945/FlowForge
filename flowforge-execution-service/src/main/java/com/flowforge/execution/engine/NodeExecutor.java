package com.flowforge.execution.engine;

import com.flowforge.common.contract.NodeType;

/**
 * 节点执行器。
 *
 * <p>一个实现只负责「怎么执行」，不碰状态机：抢占、落库、推进后继都由 {@code ExecutionEngine} 完成，
 * 这样执行器可以脱离数据库单独测试。
 */
public interface NodeExecutor {

    NodeType type();

    NodeResult execute(NodeContext context);
}
