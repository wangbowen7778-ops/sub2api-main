package com.sub2api.module.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话限制缓存服务
 * 管理账号级别的活跃会话跟踪
 *
 * Key 格式: session_limit:account:{accountId}
 * 数据结构: Sorted Set (member=sessionUUID, score=timestamp)
 *
 * 会话在空闲超时后自动过期，无需手动清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String SESSION_KEY_PREFIX = "session_limit:account:";
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(1); // 总 TTL 防止泄漏

    /**
     * 注册会话活动
     *
     * @param accountId     账号ID
     * @param sessionUUID   会话 UUID
     * @param maxSessions   最大并发会话数限制
     * @param idleTimeout   空闲超时时间
     * @return true 表示允许，false 表示超出限制
     */
    public boolean registerSession(Long accountId, String sessionUUID, int maxSessions, Duration idleTimeout) {
        if (maxSessions <= 0) {
            // 无限制
            return true;
        }

        String key = SESSION_KEY_PREFIX + accountId;
        long now = System.currentTimeMillis();
        long idleTimeoutMs = idleTimeout != null ? idleTimeout.toMillis() : DEFAULT_IDLE_TIMEOUT.toMillis();
        long expireTime = now + idleTimeoutMs;

        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // 检查会话是否已存在
            Double existingScore = zSetOps.score(key, sessionUUID);
            if (existingScore != null) {
                // 会话已存在，刷新时间戳
                zSetOps.add(key, sessionUUID, expireTime);
                redisTemplate.expire(key, SESSION_TTL);
                log.debug("Session refreshed: accountId={}, session={}", accountId, sessionUUID);
                return true;
            }

            // 获取当前活跃会话数
            // 清理过期会话
            cleanupExpiredSessions(key, now);

            Long currentCount = zSetOps.zCard(key);
            if (currentCount == null) {
                currentCount = 0L;
            }

            if (currentCount >= maxSessions) {
                log.debug("Session limit reached: accountId={}, current={}, max={}",
                        accountId, currentCount, maxSessions);
                return false;
            }

            // 添加新会话
            zSetOps.add(key, sessionUUID, expireTime);
            redisTemplate.expire(key, SESSION_TTL);
            log.debug("Session registered: accountId={}, session={}, current={}/{}",
                    accountId, sessionUUID, currentCount + 1, maxSessions);
            return true;

        } catch (Exception e) {
            log.warn("Failed to register session: accountId={}, error={}", accountId, e.getMessage());
            // 失败时允许通过（降级策略）
            return true;
        }
    }

    /**
     * 刷新现有会话的时间戳
     *
     * @param accountId   账号ID
     * @param sessionUUID 会话 UUID
     * @param idleTimeout 空闲超时时间
     */
    public void refreshSession(Long accountId, String sessionUUID, Duration idleTimeout) {
        String key = SESSION_KEY_PREFIX + accountId;
        long now = System.currentTimeMillis();
        long idleTimeoutMs = idleTimeout != null ? idleTimeout.toMillis() : DEFAULT_IDLE_TIMEOUT.toMillis();
        long expireTime = now + idleTimeoutMs;

        try {
            redisTemplate.opsForZSet().add(key, sessionUUID, expireTime);
            redisTemplate.expire(key, SESSION_TTL);
            log.debug("Session refreshed: accountId={}, session={}", accountId, sessionUUID);
        } catch (Exception e) {
            log.warn("Failed to refresh session: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    /**
     * 获取当前活跃会话数
     *
     * @param accountId 账号ID
     * @return 未过期的会话数量
     */
    public int getActiveSessionCount(Long accountId) {
        String key = SESSION_KEY_PREFIX + accountId;
        long now = System.currentTimeMillis();

        try {
            // 清理过期会话
            cleanupExpiredSessions(key, now);

            Long count = redisTemplate.opsForZSet().zCard(key);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("Failed to get active session count: accountId={}, error={}", accountId, e.getMessage());
            return 0;
        }
    }

    /**
     * 批量获取多个账号的活跃会话数
     *
     * @param accountIds   账号ID列表
     * @param idleTimeouts 每个账号的空闲超时时间配置（可为 null）
     * @return 账号ID到会话数的映射
     */
    public java.util.Map<Long, Integer> getActiveSessionCountBatch(
            java.util.List<Long> accountIds,
            java.util.Map<Long, Duration> idleTimeouts) {
        java.util.Map<Long, Integer> result = new java.util.HashMap<>();
        if (accountIds == null || accountIds.isEmpty()) {
            return result;
        }

        long now = System.currentTimeMillis();
        try {
            for (Long accountId : accountIds) {
                String key = SESSION_KEY_PREFIX + accountId;

                // 清理过期会话
                cleanupExpiredSessions(key, now);

                Long count = redisTemplate.opsForZSet().zCard(key);
                result.put(accountId, count != null ? count.intValue() : 0);
            }
        } catch (Exception e) {
            log.warn("Failed to get active session count batch: error={}", e.getMessage());
        }

        return result;
    }

    /**
     * 检查特定会话是否活跃（未过期）
     *
     * @param accountId   账号ID
     * @param sessionUUID 会话 UUID
     * @return true 表示会话活跃
     */
    public boolean isSessionActive(Long accountId, String sessionUUID) {
        String key = SESSION_KEY_PREFIX + accountId;
        long now = System.currentTimeMillis();

        try {
            Double score = redisTemplate.opsForZSet().score(key, sessionUUID);
            if (score == null) {
                return false;
            }
            return score > now;
        } catch (Exception e) {
            log.warn("Failed to check session active: accountId={}, error={}", accountId, e.getMessage());
            return false;
        }
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions(String key, long now) {
        try {
            // 移除所有分数 <= now 的成员（过期会话）
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now);
        } catch (Exception e) {
            log.warn("Failed to cleanup expired sessions: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 生成新的会话 UUID
     */
    public String generateSessionUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
