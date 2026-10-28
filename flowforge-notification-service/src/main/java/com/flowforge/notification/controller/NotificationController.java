package com.flowforge.notification.controller;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.common.security.LoginUser;
import com.flowforge.notification.dto.NotificationView;
import com.flowforge.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "通知中心", description = "站内通知查询与已读")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "查询我的通知")
    public ApiResponse<PageResult<NotificationView>> list(@AuthenticationPrincipal LoginUser user,
                                                          @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(notificationService.list(requireUser(user).userId(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读数量", description = "用于顶部角标")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(requireUser(user).userId())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记已读", description = "幂等：已读的通知重复标记不会报错")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        notificationService.markRead(requireUser(user).userId(), id);
        return ApiResponse.ok();
    }

    private LoginUser requireUser(LoginUser user) {
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
