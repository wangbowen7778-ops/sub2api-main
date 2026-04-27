package com.sub2api.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.user.mapper.UserSubscriptionMapper;
import com.sub2api.module.user.model.entity.UserSubscription;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 订阅服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService extends ServiceImpl<UserSubscriptionMapper, UserSubscription> {

    private final UserSubscriptionMapper userSubscriptionMapper;

    /**
     * 创建订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public UserSubscription createSubscription(Long userId, Long groupId, OffsetDateTime expiresAt) {
        // 检查是否已有活跃订阅
        UserSubscription existing = getActiveSubscription(userId, groupId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订阅已存在");
        }

        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setGroupId(groupId);
        subscription.setStatus("active");
        subscription.setStartsAt(OffsetDateTime.now());
        subscription.setExpiresAt(expiresAt);
        subscription.setCreatedAt(OffsetDateTime.now());
        subscription.setUpdatedAt(OffsetDateTime.now());

        if (!save(subscription)) {
            throw new BusinessException(ErrorCode.FAIL, "创建订阅失败");
        }

        log.info("创建订阅: userId={}, groupId={}, expiresAt={}",
                userId, groupId, expiresAt);
        return subscription;
    }

    /**
     * 获取用户活跃订阅
     */
    public UserSubscription getActiveSubscription(Long userId, Long groupId) {
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubscription::getUserId, userId)
                .eq(groupId != null, UserSubscription::getGroupId, groupId)
                .eq(UserSubscription::getStatus, "active")
                .gt(UserSubscription::getExpiresAt, OffsetDateTime.now())
                .isNull(UserSubscription::getDeletedAt)
                .orderByDesc(UserSubscription::getCreatedAt)
                .last("LIMIT 1");

        return getOne(wrapper);
    }

    /**
     * 获取用户所有订阅
     */
    public List<UserSubscription> getUserSubscriptions(Long userId) {
        return list(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .isNull(UserSubscription::getDeletedAt)
                .orderByDesc(UserSubscription::getCreatedAt));
    }

    /**
     * 获取用户所有活跃订阅 (Go: ListActiveUserSubscriptions)
     */
    public List<UserSubscription> listActiveByUserId(Long userId) {
        return list(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, "active")
                .isNull(UserSubscription::getDeletedAt)
                .orderByDesc(UserSubscription::getCreatedAt));
    }

    /**
     * 获取订阅详情
     */
    public UserSubscription getSubscription(Long subscriptionId) {
        UserSubscription subscription = getById(subscriptionId);
        if (subscription == null || subscription.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订阅不存在");
        }
        return subscription;
    }

    /**
     * 取消订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSubscription(Long subscriptionId) {
        UserSubscription subscription = getSubscription(subscriptionId);
        subscription.setStatus("suspended");
        subscription.setUpdatedAt(OffsetDateTime.now());
        updateById(subscription);
        log.info("取消订阅: subscriptionId={}", subscriptionId);
    }

    /**
     * 续期订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public UserSubscription renewSubscription(Long subscriptionId, OffsetDateTime newExpiresAt) {
        UserSubscription subscription = getSubscription(subscriptionId);
        subscription.setExpiresAt(newExpiresAt);
        subscription.setStatus("active");
        subscription.setUpdatedAt(OffsetDateTime.now());
        updateById(subscription);
        log.info("续期订阅: subscriptionId={}, newExpiresAt={}", subscriptionId, newExpiresAt);
        return subscription;
    }

    /**
     * 更新订阅状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSubscriptionStatus(Long subscriptionId, String status) {
        UserSubscription subscription = getSubscription(subscriptionId);
        subscription.setStatus(status);
        subscription.setUpdatedAt(OffsetDateTime.now());
        updateById(subscription);
        log.info("更新订阅状态: subscriptionId={}, status={}", subscriptionId, status);
    }

    /**
     * 删除订阅 (软删除)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSubscription(Long subscriptionId) {
        UserSubscription subscription = getSubscription(subscriptionId);
        subscription.setDeletedAt(OffsetDateTime.now());
        subscription.setUpdatedAt(OffsetDateTime.now());
        updateById(subscription);
        log.info("删除订阅: subscriptionId={}", subscriptionId);
    }

    /**
     * 检查订阅是否有效
     */
    public boolean isSubscriptionValid(Long userId, Long groupId) {
        UserSubscription subscription = getActiveSubscription(userId, groupId);
        return subscription != null;
    }

    /**
     * 获取用户剩余天数
     */
    public long getRemainingDays(Long userId, Long groupId) {
        UserSubscription subscription = getActiveSubscription(userId, groupId);
        if (subscription == null || subscription.getExpiresAt() == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(OffsetDateTime.now(), subscription.getExpiresAt());
    }

    /**
     * 过期订阅清理 (定时任务调用)
     * @return 更新了多少条订阅
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireSubscriptions() {
        LambdaQueryWrapper<UserSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubscription::getStatus, "active")
                .lt(UserSubscription::getExpiresAt, OffsetDateTime.now())
                .isNull(UserSubscription::getDeletedAt);

        List<UserSubscription> expiredSubscriptions = list(wrapper);
        for (UserSubscription subscription : expiredSubscriptions) {
            subscription.setStatus("expired");
            subscription.setUpdatedAt(OffsetDateTime.now());
            updateById(subscription);
            log.info("订阅已过期: subscriptionId={}", subscription.getId());
        }
        return expiredSubscriptions.size();
    }
}
