package com.flowforge.user.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户资料视图。不包含密码哈希、失败次数、锁定时间等认证内部字段。
 */
public record UserProfile(
        Long id,
        String username,
        String displayName,
        String email,
        List<String> roles,
        LocalDateTime createdAt
) {
}
