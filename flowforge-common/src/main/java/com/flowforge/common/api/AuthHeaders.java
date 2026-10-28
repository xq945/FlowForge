package com.flowforge.common.api;

/**
 * 内部身份头约定。
 *
 * <p>这些头**只由网关写入**，业务服务不得把它当作唯一身份依据：
 * 服务会独立校验 JWT 签名，头仅用于日志与链路排查。
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String USER_ROLES = "X-User-Roles";

    private AuthHeaders() {
    }
}
