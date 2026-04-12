package com.sub2api.module.admin.controller;

import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.UserSubscription;
import com.sub2api.module.user.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订阅管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - Subscription", description = "订阅管理接口")
@RestController
@RequestMapping("/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "创建订阅")
    @PostMapping
    public Result<UserSubscription> createSubscription(@RequestBody CreateSubscriptionRequest request) {
        UserSubscription subscription = subscriptionService.createSubscription(
                request.getUserId(),
                request.getGroupId(),
                request.getSubscriptionType(),
                request.getExpiresAt()
        );
        return Result.ok(subscription);
    }

    @Operation(summary = "获取订阅详情")
    @GetMapping("/{id}")
    public Result<UserSubscription> getSubscription(@PathVariable Long id) {
        UserSubscription subscription = subscriptionService.getSubscription(id);
        return Result.ok(subscription);
    }

    @Operation(summary = "查询用户订阅")
    @GetMapping("/user/{userId}")
    public Result<List<UserSubscription>> getUserSubscriptions(@PathVariable Long userId) {
        List<UserSubscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        return Result.ok(subscriptions);
    }

    @Operation(summary = "获取用户活跃订阅")
    @GetMapping("/user/{userId}/active")
    public Result<UserSubscription> getActiveSubscription(
            @PathVariable Long userId,
            @RequestParam(required = false) Long groupId) {
        UserSubscription subscription = subscriptionService.getActiveSubscription(userId, groupId);
        return Result.ok(subscription);
    }

    @Operation(summary = "取消订阅")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelSubscription(@PathVariable Long id) {
        subscriptionService.cancelSubscription(id);
        return Result.ok();
    }

    @Operation(summary = "续期订阅")
    @PostMapping("/{id}/renew")
    public Result<UserSubscription> renewSubscription(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newExpiresAt) {
        UserSubscription subscription = subscriptionService.renewSubscription(id, newExpiresAt);
        return Result.ok(subscription);
    }

    @Operation(summary = "更新订阅状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        subscriptionService.updateSubscriptionStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除订阅")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionService.deleteSubscription(id);
        return Result.ok();
    }

    @Operation(summary = "检查订阅是否有效")
    @GetMapping("/validate")
    public Result<Map<String, Object>> validateSubscription(
            @RequestParam Long userId,
            @RequestParam(required = false) Long groupId) {
        boolean valid = subscriptionService.isSubscriptionValid(userId, groupId);
        long remainingDays = subscriptionService.getRemainingDays(userId, groupId);
        return Result.ok(Map.of(
                "valid", valid,
                "remainingDays", remainingDays
        ));
    }

    @Operation(summary = "过期订阅清理")
    @PostMapping("/expire")
    public Result<Void> expireSubscriptions() {
        subscriptionService.expireSubscriptions();
        return Result.ok();
    }

    /**
     * 创建订阅请求
     */
    @lombok.Data
    public static class CreateSubscriptionRequest {
        private Long userId;
        private Long groupId;
        private String subscriptionType;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime expiresAt;
    }
}
