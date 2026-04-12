package com.sub2api.module.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 并发控制服务
 * 使用 Redis 管理账号和用户的并发请求槽位
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String ACCOUNT_SLOT_KEY_PREFIX = "concurrency:account:";
    private static final String USER_SLOT_KEY_PREFIX = "concurrency:user:";
    private static final Duration SLOT_TTL = Duration.ofMinutes(10);

    // 每个请求的唯一标识（简化版本，使用 UUID）
    private final Map<String, String> localRequestIds = new ConcurrentHashMap<>();

    /**
     * 尝试获取账号并发槽位
     *
     * @param accountId     账号ID
     * @param maxConcurrent  最大并发数
     * @param requestId     请求ID
     * @return 是否成功获取槽位
     */
    public boolean tryAcquireAccountSlot(Long accountId, int maxConcurrent, String requestId) {
        if (maxConcurrent <= 0) {
            // 无限制
            return true;
        }

        String key = ACCOUNT_SLOT_KEY_PREFIX + accountId;
        try {
            // 尝试获取当前并发数
            Long currentCount = redisTemplate.opsForZSet().zCard(key);
            if (currentCount == null) {
                currentCount = 0L;
            }

            if (currentCount >= maxConcurrent) {
                log.debug("账号 {} 已达最大并发数 {}，当前 {}", accountId, maxConcurrent, currentCount);
                return false;
            }

            // 添加请求到有序集合
            double score = System.currentTimeMillis();
            Boolean added = redisTemplate.opsForZSet().add(key, requestId, score);
            if (Boolean.TRUE.equals(added)) {
                // 设置 TTL 防止泄漏
                redisTemplate.expire(key, SLOT_TTL);
                log.debug("账号 {} 获取并发槽位成功，当前 {}/{}", accountId, currentCount + 1, maxConcurrent);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("获取账号并发槽位失败: accountId={}, error={}", accountId, e.getMessage());
            // 失败时允许通过（降级策略）
            return true;
        }
    }

    /**
     * 释放账号并发槽位
     *
     * @param accountId 账号ID
     * @param requestId 请求ID
     */
    public void releaseAccountSlot(Long accountId, String requestId) {
        String key = ACCOUNT_SLOT_KEY_PREFIX + accountId;
        try {
            Long removed = redisTemplate.opsForZSet().remove(key, requestId);
            if (removed != null && removed > 0) {
                log.debug("账号 {} 释放并发槽位成功: {}", accountId, requestId);
            }
        } catch (Exception e) {
            log.error("释放账号并发槽位失败: accountId={}, requestId={}, error={}", accountId, requestId, e.getMessage());
        }
    }

    /**
     * 获取账号当前并发数
     *
     * @param accountId 账号ID
     * @return 当前并发数
     */
    public int getAccountConcurrency(Long accountId) {
        String key = ACCOUNT_SLOT_KEY_PREFIX + accountId;
        try {
            Long count = redisTemplate.opsForZSet().zCard(key);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("获取账号并发数失败: accountId={}, error={}", accountId, e.getMessage());
            return 0;
        }
    }

    /**
     * 批量获取账号并发数
     *
     * @param accountIds 账号ID列表
     * @return 账号ID到并发数的映射
     */
    public Map<Long, Integer> getAccountConcurrencyBatch(List<Long> accountIds) {
        Map<Long, Integer> result = new java.util.HashMap<>();
        if (accountIds == null || accountIds.isEmpty()) {
            return result;
        }
        for (Long accountId : accountIds) {
            result.put(accountId, getAccountConcurrency(accountId));
        }
        return result;
    }

    /**
     * 尝试获取用户并发槽位
     *
     * @param userId       用户ID
     * @param maxConcurrent 最大并发数
     * @param requestId    请求ID
     * @return 是否成功获取槽位
     */
    public boolean tryAcquireUserSlot(Long userId, int maxConcurrent, String requestId) {
        if (maxConcurrent <= 0 || userId == null) {
            return true;
        }

        String key = USER_SLOT_KEY_PREFIX + userId;
        try {
            Long currentCount = redisTemplate.opsForZSet().zCard(key);
            if (currentCount == null) {
                currentCount = 0L;
            }

            if (currentCount >= maxConcurrent) {
                return false;
            }

            double score = System.currentTimeMillis();
            Boolean added = redisTemplate.opsForZSet().add(key, requestId, score);
            if (Boolean.TRUE.equals(added)) {
                redisTemplate.expire(key, SLOT_TTL);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("获取用户并发槽位失败: userId={}, error={}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * 释放用户并发槽位
     *
     * @param userId    用户ID
     * @param requestId 请求ID
     */
    public void releaseUserSlot(Long userId, String requestId) {
        if (userId == null) {
            return;
        }
        String key = USER_SLOT_KEY_PREFIX + userId;
        try {
            redisTemplate.opsForZSet().remove(key, requestId);
        } catch (Exception e) {
            log.error("释放用户并发槽位失败: userId={}, requestId={}, error={}", userId, requestId, e.getMessage());
        }
    }

    /**
     * 获取用户当前并发数
     *
     * @param userId 用户ID
     * @return 当前并发数
     */
    public int getUserConcurrency(Long userId) {
        if (userId == null) {
            return 0;
        }
        String key = USER_SLOT_KEY_PREFIX + userId;
        try {
            Long count = redisTemplate.opsForZSet().zCard(key);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("获取用户并发数失败: userId={}, error={}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 生成请求ID
     *
     * @return 请求ID
     */
    public String generateRequestId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 槽位结果
     */
    public record SlotResult(boolean acquired, String requestId, Runnable releaseFunc) {
    }

    /**
     * 尝试获取账号并发槽位（带释放函数）
     *
     * @param accountId     账号ID
     * @param maxConcurrent 最大并发数
     * @return 槽位结果，包含释放函数
     */
    public SlotResult tryAcquireWithRelease(Long accountId, int maxConcurrent) {
        String requestId = generateRequestId();
        boolean acquired = tryAcquireAccountSlot(accountId, maxConcurrent, requestId);

        if (acquired) {
            return new SlotResult(true, requestId, () -> releaseAccountSlot(accountId, requestId));
        }
        return new SlotResult(false, requestId, null);
    }
}
