package com.flowforge.common.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 依赖边。{@code conditionValue} 仅条件节点的出边允许非空，取值 TRUE / FALSE。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeSpec(String fromNodeKey, String toNodeKey, String conditionValue) {
}
