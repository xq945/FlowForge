package com.flowforge.common.contract;

/**
 * 节点类型。
 *
 * <p>第一版只开放这四种，且都不执行用户上传的任意代码：
 * HTTP 只发起受白名单约束的请求，条件节点做结构化比较，模拟节点走本地逻辑，通知节点只落库。
 */
public enum NodeType {

    /** HTTP 请求：配置 url / method / headers / body / expectStatus */
    HTTP,

    /** 条件判断：配置 field / operator / value，有且仅有 TRUE / FALSE 两条出边 */
    CONDITION,

    /** 模拟业务处理：配置 durationMs / failRate，用于演示重试与失败路径 */
    MOCK_TASK,

    /** 通知：配置 channel / title / content */
    NOTIFY;

    public static NodeType from(String value) {
        for (NodeType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
