package com.sub2api.module.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * RPM (Requests Per Minute) 缓存服务
 * 用于追踪账号每分钟请求数限制
 *
 * Key 格式: rpm:account:{accountId}:{minuteKey}
 * 使用 Redis 字符串计数器，自动过期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RpmCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String RPM_KEY_PREFIX = "rpm:account:";
    private static final Duration RPM_TTL = Duration.ofMinutes(2); // 保留2分钟的数据
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * 获取当前分钟 Key
     */
    private String getMinuteKey() {
        return LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    /**
     * 获取指定分钟的 Key
     */
    private String getMinuteKey(LocalDateTime time) {
        return time.format(MINUTE_FORMATTER);
    }

    /**
     * 获取 RPM 缓存 Key
     */
    private String getRpmKey(Long accountId, String minuteKey) {
        return RPM_KEY_PREFIX + accountId + ":" + minuteKey;
    }

    /**
     * 原子递增并返回当前分钟的计数
     *
     * @param accountId 账号ID
     * @return 当前分钟的请求计数
     */
    public int incrementAndGet(Long accountId) {
        String minuteKey = getMinuteKey();
        String key = getRpmKey(accountId, minuteKey);

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // 首次设置，设置过期时间
                redisTemplate.expire(key, RPM_TTL);
            }
            log.debug("RPM increment: accountId={}, count={}", accountId, count);
            return count != null ? count.intValue() : 1;
        } catch (Exception e) {
            log.warn("Failed to increment RPM: accountId={}, error={}", accountId, e.getMessage());
            return 1; // 失败时返回1，允许请求通过
        }
    }

    /**
     * 获取当前分钟的 RPM 计数
     *
     * @param accountId 账号ID
     * @return 当前分钟的请求计数
     */
    public int getRpm(Long accountId) {
        String minuteKey = getMinuteKey();
        String key = getRpmKey(accountId, minuteKey);

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return 0;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Failed to get RPM: accountId={}, error={}", accountId, e.getMessage());
            return 0;
        }
    }

    /**
     * 批量获取多个账号的 RPM 计数
     *
     * @param accountIds 账号ID列表
     * @return 账号ID到RPM计数的映射
     */
    public Map<Long, Integer> getRpmBatch(java.util.List<Long> accountIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (accountIds == null || accountIds.isEmpty()) {
            return result;
        }

        String minuteKey = getMinuteKey();
        try {
            for (Long accountId : accountIds) {
                String key = getRpmKey(accountId, minuteKey);
                String value = redisTemplate.opsForValue().get(key);
                int count = 0;
                if (value != null) {
                    try {
                        count = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                    }
                }
                result.put(accountId, count);
            }
        } catch (Exception e) {
            log.warn("Failed to get RPM batch: error={}", e.getMessage());
        }

        return result;
    }

    /**
     * 获取上一分钟的 RPM 计数
     *
     * @param accountId 账号ID
     * @return 上一分钟的请求计数
     */
    public int getPreviousRpm(Long accountId) {
        LocalDateTime previousMinute = LocalDateTime.now().minusMinutes(1);
        String minuteKey = getMinuteKey(previousMinute);
        String key = getRpmKey(accountId, minuteKey);

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return 0;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Failed to get previous RPM: accountId={}, error={}", accountId, e.getMessage());
            return 0;
        }
    }
}
