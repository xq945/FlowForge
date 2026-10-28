package com.flowforge.common.api;

/**
 * 全项目统一错误码。
 *
 * <p>编码规则：字母 + 4 位数字。首字母表示归属模块：
 * A 通用 / 认证，U 用户，W 工作流，S 调度，E 执行，N 通知，B 系统。
 * 客户端只依赖 code 判断类型，不解析 message。
 */
public enum ErrorCode {

    OK("0", "成功", 200),

    // ---- 通用与认证 ----
    INVALID_PARAM("A0400", "参数校验失败", 400),
    UNAUTHORIZED("A0301", "未登录或登录已过期", 401),
    FORBIDDEN("A0303", "无权访问该资源", 403),
    NOT_FOUND("A0404", "资源不存在", 404),
    STATE_CONFLICT("A0409", "资源状态冲突，请刷新后重试", 409),
    RATE_LIMITED("A0429", "请求过于频繁，请稍后再试", 429),

    // ---- 用户 ----
    USERNAME_TAKEN("U1001", "用户名已被占用", 409),
    BAD_CREDENTIALS("U1002", "用户名或密码错误", 401),
    ACCOUNT_DISABLED("U1003", "账号已被禁用", 403),
    ACCOUNT_LOCKED("U1004", "账号已锁定，请稍后再试", 423),

    // ---- 工作流 ----
    WORKFLOW_NOT_FOUND("W0404", "工作流不存在", 404),
    VERSION_NOT_FOUND("W0405", "工作流版本不存在", 404),
    WORKFLOW_NAME_TAKEN("W1001", "同名工作流已存在", 409),
    WORKFLOW_STATE_CONFLICT("W1002", "当前工作流状态不允许该操作", 409),
    WORKFLOW_INVALID("W1003", "工作流定义不合法", 400),
    WORKFLOW_NOT_PUBLISHED("W1004", "工作流尚未发布，无法执行", 409),

    // ---- 系统 ----
    INTERNAL_ERROR("B0500", "服务器内部错误", 500),
    SERVICE_UNAVAILABLE("B0503", "依赖服务暂时不可用", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
