package com.flowforge.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不合法")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不合法")
        String password
) {
}
