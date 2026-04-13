package com.sub2api.module.billing.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Billing Cache Service
 * 计费缓存服务，提供余额、订阅、API Key 限流等缓存操作
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCacheService {

    private final StringRedisTemplate redisTemplate;

    // 缓存 key 前缀
    private static final String BALANCE_PREFIX = "billing:balance:";
    private static final String SUBSCRIPTION_PREFIX = "billing:subscription:";
    private static final String API_KEY_RATE_LIMIT_PREFIX = "billing:apikey:ratelimit:";

    // 缓存 TTL
    private static final Duration BALANCE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration SUBSCRIPTION_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_CACHE_TTL = Duration.ofHours(24);

    /**
     * API Key 限流缓存数据
     */
    @Data
    public static class APIKeyRateLimitCacheData {
        @JsonProperty("usage_5h")
        private double usage5h;

        @JsonProperty("usage_1d")
        private double usage1d;

        @JsonProperty("usage_7d")
        private double usage7d;

        @JsonProperty("window_5h")
        private long window5h;

        @JsonProperty("window_1d")
        private long window1d;

        @JsonProperty("window_7d")
        private long window7d;

        /**
         * 计算有效使用量（窗口过期返回0）
         */
        public double effectiveUsage5h() {
            return isWindowExpired(window5h, 5) ? 0 : usage5h;
        }

        public double effectiveUsage1d() {
            return isWindowExpired(window1d, 24) ? 0 : usage1d;
        }

        public double effectiveUsage7d() {
            return isWindowExpired(window7d, 24 * 7) ? 0 : usage7d;
        }

        private boolean isWindowExpired(long windowStart, int hours) {
            if (windowStart == 0) {
                return true;
            }
            long windowEnd = windowStart + (hours * 3600L);
            return Instant.now().getEpochSecond() > windowEnd;
        }
    }

    /**
     * 订阅缓存数据
     */
    @Data
    public static class SubscriptionCacheData {
        private Long subscriptionId;
        private String status;
        private Double quota;
        private Double quotaUsed;
        private LocalDateTime expiresAt;
    }

    // ========== Balance Operations ==========

    /**
     * 获取用户余额
     */
    public double getUserBalance(Long userId) {
        String key = BALANCE_PREFIX + userId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && !value.isBlank()) {
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            log.warn("Failed to get user balance from cache: userId={}, error={}", userId, e.getMessage());
        }
        return -1; // 返回 -1 表示未缓存
    }

    /**
     * 设置用户余额
     */
    public void setUserBalance(Long userId, double balance) {
        String key = BALANCE_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(balance), BALANCE_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to set user balance in cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 扣除用户余额
     */
    public boolean deductUserBalance(Long userId, double amount) {
        String key = BALANCE_PREFIX + userId;
        try {
            // 使用 Lua 脚本保证原子性
            String script = """
                local current = redis.call('GET', KEYS[1])
                if current == false then
                    return -1
                end
                local balance = tonumber(current)
                local amount = tonumber(ARGV[1])
                if balance < amount then
                    return -2
                end
                local newBalance = balance - amount
                redis.call('SET', KEYS[1], tostring(newBalance))
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return newBalance
                """;
            Long result = redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                    java.util.List.of(key),
                    String.valueOf(amount),
                    String.valueOf(BALANCE_CACHE_TTL.toSeconds())
            );

            if (result != null && result == -1) {
                log.warn("Balance not found in cache: userId={}", userId);
                return false;
            }
            if (result != null && result == -2) {
                log.warn("Insufficient balance: userId={}, amount={}", userId, amount);
                return false;
            }
            return result != null && result >= 0;

        } catch (Exception e) {
            log.error("Failed to deduct user balance: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 使用户余额缓存失效
     */
    public void invalidateUserBalance(Long userId) {
        String key = BALANCE_PREFIX + userId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate user balance cache: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ========== Subscription Operations ==========

    /**
     * 获取订阅缓存
     */
    public SubscriptionCacheData getSubscriptionCache(Long userId, Long groupId) {
        String key = SUBSCRIPTION_PREFIX + userId + ":" + groupId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, SubscriptionCacheData.class);
            }
        } catch (Exception e) {
            log.warn("Failed to get subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
        return null;
    }

    /**
     * 设置订阅缓存
     */
    public void setSubscriptionCache(Long userId, Long groupId, SubscriptionCacheData data) {
        String key = SUBSCRIPTION_PREFIX + userId + ":" + groupId;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, SUBSCRIPTION_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to set subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    /**
     * 更新订阅使用量
     */
    public void updateSubscriptionUsage(Long userId, Long groupId, double cost) {
        String key = SUBSCRIPTION_PREFIX + userId + ":" + groupId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                SubscriptionCacheData data = mapper.readValue(json, SubscriptionCacheData.class);
                if (data.getQuotaUsed() != null) {
                    data.setQuotaUsed(data.getQuotaUsed() + cost);
                } else {
                    data.setQuotaUsed(cost);
                }
                String newJson = mapper.writeValueAsString(data);
                redisTemplate.opsForValue().set(key, newJson, SUBSCRIPTION_CACHE_TTL);
            }
        } catch (Exception e) {
            log.warn("Failed to update subscription usage: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    /**
     * 使订阅缓存失效
     */
    public void invalidateSubscriptionCache(Long userId, Long groupId) {
        String key = SUBSCRIPTION_PREFIX + userId + ":" + groupId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate subscription cache: userId={}, groupId={}, error={}", userId, groupId, e.getMessage());
        }
    }

    // ========== API Key Rate Limit Operations ==========

    /**
     * 获取 API Key 限流数据
     */
    public APIKeyRateLimitCacheData getAPIKeyRateLimit(Long keyId) {
        String key = API_KEY_RATE_LIMIT_PREFIX + keyId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, APIKeyRateLimitCacheData.class);
            }
        } catch (Exception e) {
            log.warn("Failed to get API key rate limit: keyId={}, error={}", keyId, e.getMessage());
        }
        return null;
    }

    /**
     * 设置 API Key 限流数据
     */
    public void setAPIKeyRateLimit(Long keyId, APIKeyRateLimitCacheData data) {
        String key = API_KEY_RATE_LIMIT_PREFIX + keyId;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, RATE_LIMIT_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to set API key rate limit: keyId={}, error={}", keyId, e.getMessage());
        }
    }

    /**
     * 更新 API Key 使用量
     */
    public void updateAPIKeyRateLimitUsage(Long keyId, double cost) {
        String key = API_KEY_RATE_LIMIT_PREFIX + keyId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            APIKeyRateLimitCacheData data;

            if (json != null && !json.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                data = mapper.readValue(json, APIKeyRateLimitCacheData.class);
            } else {
                data = new APIKeyRateLimitCacheData();
            }

            long now = Instant.now().getEpochSecond();

            // 更新 5h 窗口
            if (data.getWindow5h() == 0 || now - data.getWindow5h() > 5 * 3600) {
                data.setUsage5h(cost);
                data.setWindow5h(now);
            } else {
                data.setUsage5h(data.getUsage5h() + cost);
            }

            // 更新 1d 窗口
            if (data.getWindow1d() == 0 || now - data.getWindow1d() > 24 * 3600) {
                data.setUsage1d(cost);
                data.setWindow1d(now);
            } else {
                data.setUsage1d(data.getUsage1d() + cost);
            }

            // 更新 7d 窗口
            if (data.getWindow7d() == 0 || now - data.getWindow7d() > 7 * 24 * 3600) {
                data.setUsage7d(cost);
                data.setWindow7d(now);
            } else {
                data.setUsage7d(data.getUsage7d() + cost);
            }

            // 保存
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String newJson = mapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, newJson, RATE_LIMIT_CACHE_TTL);

        } catch (Exception e) {
            log.warn("Failed to update API key rate limit usage: keyId={}, error={}", keyId, e.getMessage());
        }
    }

    /**
     * 使 API Key 限流缓存失效
     */
    public void invalidateAPIKeyRateLimit(Long keyId) {
        String key = API_KEY_RATE_LIMIT_PREFIX + keyId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate API key rate limit cache: keyId={}, error={}", keyId, e.getMessage());
        }
    }

    /**
     * 检查 API Key 是否超限
     */
    public boolean isAPIKeyRateLimitExceeded(Long keyId, double limit5h, double limit1d, double limit7d) {
        APIKeyRateLimitCacheData data = getAPIKeyRateLimit(keyId);
        if (data == null) {
            return false;
        }

        // 检查各窗口
        if (data.effectiveUsage5h() >= limit5h) {
            return true;
        }
        if (data.effectiveUsage1d() >= limit1d) {
            return true;
        }
        if (data.effectiveUsage7d() >= limit7d) {
            return true;
        }

        return false;
    }

    /**
     * 获取 API Key 各窗口剩余额度
     */
    public Map<String, Double> getAPIKeyRemainingQuota(Long keyId, double limit5h, double limit1d, double limit7d) {
        APIKeyRateLimitCacheData data = getAPIKeyRateLimit(keyId);
        Map<String, Double> result = new ConcurrentHashMap<>();

        if (data == null) {
            result.put("remaining5h", limit5h);
            result.put("remaining1d", limit1d);
            result.put("remaining7d", limit7d);
            return result;
        }

        result.put("remaining5h", Math.max(0, limit5h - data.effectiveUsage5h()));
        result.put("remaining1d", Math.max(0, limit1d - data.effectiveUsage1d()));
        result.put("remaining7d", Math.max(0, limit7d - data.effectiveUsage7d()));

        return result;
    }
}
