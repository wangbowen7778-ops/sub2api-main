package com.sub2api.module.billing.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 计费缓存实现
 * 提供余额、订阅、API Key 限流的缓存管理
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisBillingCache {

    private final StringRedisTemplate redisTemplate;

    private static final String BALANCE_CACHE_PREFIX = "billing:balance:";
    private static final String SUBSCRIPTION_CACHE_PREFIX = "billing:subscription:";
    private static final String RATE_LIMIT_CACHE_PREFIX = "billing:ratelimit:";
    private static final long BALANCE_CACHE_TTL_SECONDS = 300;
    private static final long SUBSCRIPTION_CACHE_TTL_SECONDS = 60;
    private static final long RATE_LIMIT_CACHE_TTL_SECONDS = 300;

    /**
     * 订阅缓存数据
     */
    @Data
    public static class SubscriptionCacheData {
        private String status;
        private String expiresAt;
        private double dailyUsage;
        private double weeklyUsage;
        private double monthlyUsage;
        private long version;
    }

    /**
     * API Key 限流缓存数据
     */
    @Data
    public static class APIKeyRateLimitCacheData {
        private double usage5h;
        private double usage1d;
        private double usage7d;
        private long window5h;
        private long window1d;
        private long window7d;
    }

    // ==================== 余额操作 ====================

    /**
     * 获取用户余额
     */
    public BigDecimal getUserBalance(Long userId) {
        try {
            String key = BALANCE_CACHE_PREFIX + userId;
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return new BigDecimal(value);
        } catch (Exception e) {
            log.warn("Failed to get user balance from cache: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 设置用户余额
     */
    public void setUserBalance(Long userId, BigDecimal balance) {
        try {
            String key = BALANCE_CACHE_PREFIX + userId;
            redisTemplate.opsForValue().set(key, balance.toString(), BALANCE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to set user balance to cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 扣除用户余额
     */
    public void deductUserBalance(Long userId, BigDecimal amount) {
        try {
            String key = BALANCE_CACHE_PREFIX + userId;
            String current = redisTemplate.opsForValue().get(key);
            if (current != null) {
                BigDecimal newBalance = new BigDecimal(current).subtract(amount);
                redisTemplate.opsForValue().set(key, newBalance.toString(), BALANCE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to deduct user balance from cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 使用户余额缓存失效
     */
    public void invalidateUserBalance(Long userId) {
        try {
            String key = BALANCE_CACHE_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate user balance cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ==================== 订阅操作 ====================

    /**
     * 获取订阅缓存
     */
    public SubscriptionCacheData getSubscriptionCache(Long userId, Long groupId) {
        try {
            String key = SUBSCRIPTION_CACHE_PREFIX + userId + ":" + groupId;
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, SubscriptionCacheData.class);
        } catch (Exception e) {
            log.warn("Failed to get subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
            return null;
        }
    }

    /**
     * 设置订阅缓存
     */
    public void setSubscriptionCache(Long userId, Long groupId, SubscriptionCacheData data) {
        try {
            String key = SUBSCRIPTION_CACHE_PREFIX + userId + ":" + groupId;
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, SUBSCRIPTION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to set subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    /**
     * 更新订阅使用量
     */
    public void updateSubscriptionUsage(Long userId, Long groupId, double cost) {
        try {
            String key = SUBSCRIPTION_CACHE_PREFIX + userId + ":" + groupId;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                SubscriptionCacheData data = new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, SubscriptionCacheData.class);
                data.setDailyUsage(data.getDailyUsage() + cost);
                data.setWeeklyUsage(data.getWeeklyUsage() + cost);
                data.setMonthlyUsage(data.getMonthlyUsage() + cost);
                data.setVersion(data.getVersion() + 1);
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
                redisTemplate.opsForValue().set(key, json, SUBSCRIPTION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to update subscription usage cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    /**
     * 使订阅缓存失效
     */
    public void invalidateSubscriptionCache(Long userId, Long groupId) {
        try {
            String key = SUBSCRIPTION_CACHE_PREFIX + userId + ":" + groupId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    // ==================== API Key 限流操作 ====================

    /**
     * 获取 API Key 限流缓存
     */
    public APIKeyRateLimitCacheData getAPIKeyRateLimit(Long keyId) {
        try {
            String key = RATE_LIMIT_CACHE_PREFIX + keyId;
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, APIKeyRateLimitCacheData.class);
        } catch (Exception e) {
            log.warn("Failed to get API key rate limit cache: keyId={}, error={}", keyId, e.getMessage());
            return null;
        }
    }

    /**
     * 设置 API Key 限流缓存
     */
    public void setAPIKeyRateLimit(Long keyId, APIKeyRateLimitCacheData data) {
        try {
            String key = RATE_LIMIT_CACHE_PREFIX + keyId;
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, RATE_LIMIT_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to set API key rate limit cache: keyId={}, error={}", keyId, e.getMessage());
        }
    }

    /**
     * 更新 API Key 限流使用量
     */
    public void updateAPIKeyRateLimitUsage(Long keyId, double cost) {
        try {
            String key = RATE_LIMIT_CACHE_PREFIX + keyId;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                APIKeyRateLimitCacheData data = new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, APIKeyRateLimitCacheData.class);
                data.setUsage5h(data.getUsage5h() + cost);
                data.setUsage1d(data.getUsage1d() + cost);
                data.setUsage7d(data.getUsage7d() + cost);
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
                redisTemplate.opsForValue().set(key, json, RATE_LIMIT_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to update API key rate limit usage cache: keyId={}, error={}", keyId, e.getMessage());
        }
    }

    /**
     * 使 API Key 限流缓存失效
     */
    public void invalidateAPIKeyRateLimit(Long keyId) {
        try {
            String key = RATE_LIMIT_CACHE_PREFIX + keyId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate API key rate limit cache: keyId={}, error={}", keyId, e.getMessage());
        }
    }
}
