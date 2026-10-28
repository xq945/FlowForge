package com.flowforge.workflow.domain;

import java.util.List;

/**
 * DAG 校验结果。
 *
 * <p>返回**全部**错误而不是遇到第一个就返回：用户改图时一次看到所有问题，
 * 比反复提交试探要省事得多。
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }
}
