package com.flowforge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "用户名需为 4-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称不能超过 64 个字符")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符")
        String email
) {
}
