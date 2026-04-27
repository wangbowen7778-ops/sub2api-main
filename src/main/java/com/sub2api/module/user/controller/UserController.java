package com.sub2api.module.user.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.model.entity.UsageLog;
import com.sub2api.module.billing.service.UsageLogService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户个人中心控制器
 * 路径: /api/v1/user
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "用户", description = "用户个人接口")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final UsageLogService usageLogService;

    /**
     * 从 SecurityContext 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        if (principal instanceof com.sub2api.module.apikey.model.vo.ApiKeyInfo apiKeyInfo) {
            return apiKeyInfo.getUserId();
        }
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    /**
     * 获取当前用户信息
     * GET /api/v1/user/profile
     */
    @Operation(summary = "获取用户资料")
    @GetMapping("/profile")
    public Result<User> getProfile() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        // 清除敏感信息
        user.setPasswordHash(null);
        user.setTotpSecretEncrypted(null);

        return Result.ok(user);
    }

    /**
     * 更新用户资料
     * PUT /api/v1/user
     */
    @Operation(summary = "更新用户资料")
    @PutMapping
    public Result<User> updateProfile(@RequestBody Map<String, Object> params) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        String username = (String) params.get("username");
        User user = userService.updateProfile(userId, username);

        // 清除敏感信息
        user.setPasswordHash(null);
        user.setTotpSecretEncrypted(null);

        return Result.ok(user);
    }

    /**
     * 修改密码
     * PUT /api/v1/user/password
     */
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        String oldPassword = params.get("old_password");
        String newPassword = params.get("new_password");

        if (oldPassword == null || oldPassword.isBlank()) {
            return Result.fail(2031, "旧密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Result.fail(2031, "新密码不能为空");
        }
        if (newPassword.length() < 8) {
            return Result.fail(2031, "新密码长度必须不少于8位");
        }

        userService.changePassword(userId, oldPassword, newPassword);
        return Result.ok();
    }
}
