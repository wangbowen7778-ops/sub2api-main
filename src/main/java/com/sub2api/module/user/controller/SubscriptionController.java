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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户订阅控制器
 * 提供用户查看自己订阅的 API
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "用户订阅", description = "用户订阅相关接口")
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final GroupService groupService;
    private final UserService userService;

    /**
     * 获取当前用户的订阅列表
     */
    @Operation(summary = "获取用户订阅列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getMySubscriptions() {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .map(this::convertToSubscriptionVO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取当前用户的活跃订阅列表
     */
    @Operation(summary = "获取活跃订阅")
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> getActiveSubscriptions() {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::convertToSubscriptionVO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取订阅进度
     */
    @Operation(summary = "获取订阅进度列表")
    @GetMapping("/progress")
    public Result<List<Map<String, Object>>> getSubscriptionsProgress() {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        List<Map<String, Object>> result = subscriptions.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::buildProgressVO)
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 获取订阅摘要
     */
    @Operation(summary = "获取订阅摘要")
    @GetMapping("/summary")
    public Result<Map<String, Object>> getSubscriptionSummary() {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        List<Map<String, Object>> activeSubscriptions = subscriptions.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::convertToSubscriptionVO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("active_count", activeSubscriptions.size());
        result.put("subscriptions", activeSubscriptions);

        return Result.ok(result);
    }

    /**
     * 获取特定订阅的进度
     */
    @Operation(summary = "获取订阅进度")
    @GetMapping("/{id}/progress")
    public Result<Map<String, Object>> getSubscriptionProgress(@PathVariable Long id) {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        UserSubscription subscription = subscriptionService.getSubscription(id);
        if (!subscription.getUserId().equals(userId)) {
            return Result.fail(4031, "无权访问此订阅");
        }

        return Result.ok(buildProgressVO(subscription));
    }

    /**
     * 将订阅实体转换为 VO
     */
    private Map<String, Object> convertToSubscriptionVO(UserSubscription subscription) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", subscription.getId());
        vo.put("group_name", getGroupName(subscription.getGroupId()));
        vo.put("status", subscription.getStatus());
        vo.put("expires_at", subscription.getExpiresAt());
        vo.put("days_remaining", calculateDaysRemaining(subscription));
        return vo;
    }

    /**
     * 构建进度 VO
     */
    private Map<String, Object> buildProgressVO(UserSubscription subscription) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", subscription.getId());
        vo.put("group_id", subscription.getGroupId());
        vo.put("group_name", getGroupName(subscription.getGroupId()));
        vo.put("status", subscription.getStatus());

        // 计算进度相关字段
        if (subscription.getExpiresAt() != null) {
            long totalDays = ChronoUnit.DAYS.between(subscription.getStartsAt(), subscription.getExpiresAt());
            long usedDays = ChronoUnit.DAYS.between(subscription.getStartsAt(), OffsetDateTime.now());
            long remainingDays = ChronoUnit.DAYS.between(OffsetDateTime.now(), subscription.getExpiresAt());

            if (totalDays > 0) {
                double dailyProgress = (double) usedDays / totalDays * 100;
                vo.put("daily_progress", Math.min(100, Math.max(0, dailyProgress)));
            } else {
                vo.put("daily_progress", 0);
            }

            vo.put("weekly_progress", null); // 周进度需要更复杂的计算
            vo.put("monthly_progress", null); // 月进度需要更复杂的计算
            vo.put("days_remaining", Math.max(0, remainingDays));
        } else {
            vo.put("daily_progress", null);
            vo.put("weekly_progress", null);
            vo.put("monthly_progress", null);
            vo.put("days_remaining", null);
        }

        vo.put("expires_at", subscription.getExpiresAt());
        return vo;
    }

    /**
     * 获取分组名称
     */
    private String getGroupName(Long groupId) {
        if (groupId == null) {
            return null;
        }
        Group group = groupService.findById(groupId);
        return group != null ? group.getName() : null;
    }

    /**
     * 计算剩余天数
     */
    private Long calculateDaysRemaining(UserSubscription subscription) {
        if (subscription.getExpiresAt() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), subscription.getExpiresAt());
        return Math.max(0, days);
    }
}
