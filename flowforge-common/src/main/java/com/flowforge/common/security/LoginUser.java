package com.flowforge.common.security;

import java.util.List;

/**
 * 认证后的当前用户。
 *
 * <p>只包含判断权限必需的信息，不携带密码哈希等敏感字段。
 * 作为 Spring Security 的 principal 注入到 Controller 参数中。
 */
public record LoginUser(Long userId, String username, List<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
