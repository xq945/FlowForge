package com.flowforge.user.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.user.dto.LoginRequest;
import com.flowforge.user.dto.LoginResponse;
import com.flowforge.user.dto.RegisterRequest;
import com.flowforge.user.dto.UserProfile;
import com.flowforge.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "认证", description = "注册与登录")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册", description = "注册成功后自动绑定 USER 角色，不自动登录")
    public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "连续失败 5 次锁定 10 分钟")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
