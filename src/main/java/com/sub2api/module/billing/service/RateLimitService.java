package com.sub2api.module.billing.service;

import com.sub2api.module.common.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 限流服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    /**
     * 检查 RPM (请求频率限制)
     */
    public void checkRpm(String key, int limit) {
        check(key, "rpm", limit, 60, TimeUnit.SECONDS);
    }

    /**
     * 检查 TPM (Token 频率限制)
     */
    public void checkTpm(String key, long limit) {
        check(key, "tpm", (int) limit, 60, TimeUnit.SECONDS);
    }

    /**
     * 检查并发数
     */
    public void checkConcurrency(String key, int limit) {
        String redisKey = RATE_LIMIT_PREFIX + "concurrency:" + key;
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            int current = value != null ? Integer.parseInt(value) : 0;
            if (current >= limit) {
                throw new RateLimitException("并发数超限: " + current + "/" + limit);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检查并发限流失败: {}", e.getMessage());
        }
    }

    /**
     * 增加计数
     */
    public void increment(String key, int ttlSeconds) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("增加限流计数失败: {}", e.getMessage());
        }
    }

    /**
     * 增加 Token 计数
     */
    public void incrementTokens(String key, long tokens, int ttlSeconds) {
        String redisKey = RATE_LIMIT_PREFIX + "tpm:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey, tokens);
            if (count != null && count == tokens) {
                redisTemplate.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("增加 Token 计数失败: {}", e.getMessage());
        }
    }

    /**
     * 滑动窗口限流检查
     */
    public boolean checkSlidingWindow(String key, int limit, int windowSeconds) {
        String redisKey = RATE_LIMIT_PREFIX + "sliding:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        try {
            // 删除窗口外的记录
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

            // 获取当前窗口内的请求数
            Long count = redisTemplate.opsForZSet().zCard(redisKey);
            if (count != null && count >= limit) {
                return false;
            }

            // 添加当前请求
            redisTemplate.opsForZSet().add(redisKey, String.valueOf(now), now);
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            log.warn("滑动窗口限流检查失败: {}", e.getMessage());
            return true; // 失败时放行
        }
    }

    /**
     * 通用限流检查
     */
    private void check(String key, String type, int limit, int ttlSeconds, TimeUnit timeUnit) {
        String redisKey = RATE_LIMIT_PREFIX + type + ":" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, ttlSeconds, timeUnit);
            }
            if (count != null && count > limit) {
                throw new RateLimitException(type.toUpperCase() + " 限流: " + count + "/" + limit);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检查限流失败: {}", e.getMessage());
        }
    }
}
