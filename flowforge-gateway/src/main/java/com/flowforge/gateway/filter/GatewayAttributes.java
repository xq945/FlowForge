package com.flowforge.gateway.filter;

/**
 * 网关内部过滤器之间传递的交换属性。
 *
 * <p>用 attribute 而不是请求头传递身份：白名单路径不经过 JWT 校验，
 * 请求头仍由客户端控制，攻击者只要自己塞一个 {@code X-User-Id} 就能把限流键改成任意值。
 * attribute 只存在于网关进程内，客户端无法触碰。
 */
public final class GatewayAttributes {

    /** 已通过 JWT 校验的用户 ID（取自 token 的 sub） */
    public static final String USER_ID = GatewayAttributes.class.getName() + ".userId";

    private GatewayAttributes() {
    }
}
