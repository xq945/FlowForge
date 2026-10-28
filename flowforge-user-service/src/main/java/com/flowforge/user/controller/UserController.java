package com.flowforge.user.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.user.dto.UserProfile;
import com.flowforge.user.security.LoginUser;
import com.flowforge.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "用户", description = "当前用户资料")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户资料")
    public ApiResponse<UserProfile> me(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(authService.profileOf(loginUser.userId()));
    }
}
