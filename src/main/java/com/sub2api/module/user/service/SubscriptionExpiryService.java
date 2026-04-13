package com.sub2api.module.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Subscription Expiry Service
 * 订阅过期服务 - 定期检查并更新过期的订阅状态
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionService subscriptionService;

    private static final long EXPIRY_CHECK_TIMEOUT_SECONDS = 10;

    /**
     * 定期检查并更新过期订阅
     * 每分钟执行一次
     */
    @Scheduled(fixedDelay = 60000)
    public void runOnce() {
        try {
            int updated = subscriptionService.expireSubscriptions();
            if (updated > 0) {
                log.info("Updated {} expired subscriptions", updated);
            }
        } catch (Exception e) {
            log.error("Failed to update expired subscriptions: {}", e.getMessage());
        }
    }
}
