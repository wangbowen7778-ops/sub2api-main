package com.sub2api.module.user.controller;

import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.service.GroupService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.UserSubscription;
import com.sub2api.module.user.service.SubscriptionService;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户订阅控制器
 * 路径: /api/v1/subscriptions
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "用户订阅", description = "用户订阅相关接口")
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final GroupService groupService;
    private final UserService userService;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 从 SecurityContext 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        return userService.getCurrentUserId();
    }

    /**
     * 获取当前用户的订阅列表
     * GET /api/v1/subscriptions
     */
    @Operation(summary = "获取用户订阅列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getMySubscriptions() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .map(this::toUserSubscriptionDTO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取当前用户的活跃订阅列表
     * GET /api/v1/subscriptions/active
     */
    @Operation(summary = "获取活跃订阅")
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> getActiveSubscriptions() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        List<UserSubscription> subscriptions = subscriptionService.listActiveByUserId(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::toUserSubscriptionDTO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取订阅进度
     * GET /api/v1/subscriptions/progress
     */
    @Operation(summary = "获取订阅进度列表")
    @GetMapping("/progress")
    public Result<List<Map<String, Object>>> getSubscriptionsProgress() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        List<UserSubscription> subscriptions = subscriptionService.listActiveByUserId(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::toProgressDTO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取订阅摘要
     * GET /api/v1/subscriptions/summary
     */
    @Operation(summary = "获取订阅摘要")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getSubscriptionSummary() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        List<UserSubscription> subscriptions = subscriptionService.listActiveByUserId(userId);
        List<Map<String, Object>> activeSubscriptions = subscriptions.stream()
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::toSubscriptionSummaryItem)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("active_count", activeSubscriptions.size());
        result.put("subscriptions", activeSubscriptions);

        return Result.ok(result);
    }

    /**
     * 获取特定订阅的进度
     * GET /api/v1/subscriptions/:id/progress
     */
    @Operation(summary = "获取订阅进度")
    @GetMapping("/{id}/progress")
    public Result<Map<String, Object>> getSubscriptionProgress(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        UserSubscription subscription = subscriptionService.getSubscription(id);
        if (!subscription.getUserId().equals(userId)) {
            return Result.fail(4031, "无权访问此订阅");
        }

        return Result.ok(toProgressDTO(subscription));
    }

    /**
     * 转换为用户订阅 DTO
     * 对应 Go dto.UserSubscription
     */
    private Map<String, Object> toUserSubscriptionDTO(UserSubscription subscription) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", subscription.getId());
        dto.put("user_id", subscription.getUserId());
        dto.put("group_id", subscription.getGroupId());
        dto.put("status", subscription.getStatus());
        dto.put("starts_at", formatDateTime(subscription.getStartsAt()));
        dto.put("expires_at", formatDateTime(subscription.getExpiresAt()));
        dto.put("daily_usage_usd", subscription.getDailyUsageUsd() != null ? subscription.getDailyUsageUsd().doubleValue() : 0);
        dto.put("weekly_usage_usd", subscription.getWeeklyUsageUsd() != null ? subscription.getWeeklyUsageUsd().doubleValue() : 0);
        dto.put("monthly_usage_usd", subscription.getMonthlyUsageUsd() != null ? subscription.getMonthlyUsageUsd().doubleValue() : 0);
        dto.put("daily_window_start", formatDateTime(subscription.getDailyWindowStart()));
        dto.put("weekly_window_start", formatDateTime(subscription.getWeeklyWindowStart()));
        dto.put("monthly_window_start", formatDateTime(subscription.getMonthlyWindowStart()));
        dto.put("created_at", formatDateTime(subscription.getCreatedAt()));
        dto.put("updated_at", formatDateTime(subscription.getUpdatedAt()));
        return dto;
    }

    /**
     * 转换为订阅摘要项 DTO
     * 对应 Go handler.SubscriptionSummaryItem
     */
    private Map<String, Object> toSubscriptionSummaryItem(UserSubscription subscription) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", subscription.getId());
        dto.put("group_id", subscription.getGroupId());
        dto.put("status", subscription.getStatus());
        dto.put("daily_used_usd", subscription.getDailyUsageUsd() != null ? subscription.getDailyUsageUsd().doubleValue() : 0);
        dto.put("weekly_used_usd", subscription.getWeeklyUsageUsd() != null ? subscription.getWeeklyUsageUsd().doubleValue() : 0);
        dto.put("monthly_used_usd", subscription.getMonthlyUsageUsd() != null ? subscription.getMonthlyUsageUsd().doubleValue() : 0);

        // 获取分组名称
        Group group = groupService.findById(subscription.getGroupId());
        if (group != null) {
            dto.put("group_name", group.getName());
            if (group.getDailyLimitUsd() != null) {
                dto.put("daily_limit_usd", group.getDailyLimitUsd().doubleValue());
            }
            if (group.getWeeklyLimitUsd() != null) {
                dto.put("weekly_limit_usd", group.getWeeklyLimitUsd().doubleValue());
            }
            if (group.getMonthlyLimitUsd() != null) {
                dto.put("monthly_limit_usd", group.getMonthlyLimitUsd().doubleValue());
            }
        }

        dto.put("expires_at", formatDateTime(subscription.getExpiresAt()));
        return dto;
    }

    /**
     * 转换为进度 DTO
     * 对应 Go handler.SubscriptionProgressInfo
     */
    private Map<String, Object> toProgressDTO(UserSubscription subscription) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("subscription", toUserSubscriptionDTO(subscription));

        // 计算进度
        Map<String, Object> progress = new HashMap<>();
        if (subscription.getExpiresAt() != null) {
            long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(OffsetDateTime.now(), subscription.getExpiresAt());
            dto.put("expires_at", formatDateTime(subscription.getExpiresAt()));
            dto.put("days_remaining", Math.max(0, daysRemaining));
        }

        // 简单的日/周/月进度（基于窗口时间）
        if (subscription.getDailyWindowStart() != null) {
            long used = subscription.getDailyUsageUsd() != null ? subscription.getDailyUsageUsd().multiply(java.math.BigDecimal.valueOf(100)).longValue() : 0;
            progress.put("daily", createUsageProgress(used, 100, subscription.getDailyWindowStart()));
        }
        if (subscription.getWeeklyWindowStart() != null) {
            long used = subscription.getWeeklyUsageUsd() != null ? subscription.getWeeklyUsageUsd().multiply(java.math.BigDecimal.valueOf(100)).longValue() : 0;
            progress.put("weekly", createUsageProgress(used, 100, subscription.getWeeklyWindowStart()));
        }
        if (subscription.getMonthlyWindowStart() != null) {
            long used = subscription.getMonthlyUsageUsd() != null ? subscription.getMonthlyUsageUsd().multiply(java.math.BigDecimal.valueOf(100)).longValue() : 0;
            progress.put("monthly", createUsageProgress(used, 100, subscription.getMonthlyWindowStart()));
        }

        dto.put("progress", progress);
        return dto;
    }

    private Map<String, Object> createUsageProgress(long used, long limit, OffsetDateTime windowStart) {
        Map<String, Object> p = new HashMap<>();
        p.put("used", used);
        p.put("limit", limit);
        p.put("percentage", limit > 0 ? (used * 100.0 / limit) : 0);
        p.put("reset_in_seconds", calculateResetSeconds(windowStart));
        return p;
    }

    private Long calculateResetSeconds(OffsetDateTime windowStart) {
        if (windowStart == null) return null;
        long seconds = java.time.temporal.ChronoUnit.SECONDS.between(OffsetDateTime.now(), windowStart);
        return Math.max(0, seconds);
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(ISO_FORMATTER);
    }
}
