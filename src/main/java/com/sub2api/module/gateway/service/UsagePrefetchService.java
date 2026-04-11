package com.sub2api.module.gateway.service;

import com.sub2api.module.billing.mapper.UsageLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量预取服务
 * 批量预取账号在当前时间窗口内的用量，避免 N+1 查询问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsagePrefetchService {

    private final UsageLogMapper usageLogMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String USAGE_WINDOW_KEY_PREFIX = "usage:window:";
    private static final Duration WINDOW_CACHE_TTL = Duration.ofSeconds(30);

    /**
     * 预取多个账号在当前时间窗口内的用量
     * 返回每个账号的窗口用量成本
     */
    public Map<Long, Double> prefetchWindowCosts(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Double> results = new HashMap<>();

        // 计算当前时间窗口
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime windowEnd = windowStart.plusHours(1);

        // 尝试从缓存获取
        int cacheHit = 0;
        int cacheMiss = 0;

        for (Long accountId : accountIds) {
            String cacheKey = getCacheKey(accountId, windowStart);
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    results.put(accountId, Double.parseDouble(cached));
                    cacheHit++;
                } else {
                    cacheMiss++;
                }
            } catch (Exception e) {
                log.warn("获取用量缓存失败: accountId={}", accountId);
            }
        }

        // 缓存未命中的账号需要从数据库查询
        if (cacheMiss > 0) {
            List<Long> missedIds = accountIds.stream()
                    .filter(id -> !results.containsKey(id))
                    .toList();

            Map<Long, Double> fetched = fetchWindowCostsFromDB(missedIds, windowStart, windowEnd);

            // 写入缓存
            for (Map.Entry<Long, Double> entry : fetched.entrySet()) {
                String cacheKey = getCacheKey(entry.getKey(), windowStart);
                try {
                    redisTemplate.opsForValue().set(cacheKey, String.valueOf(entry.getValue()), WINDOW_CACHE_TTL);
                } catch (Exception e) {
                    log.warn("设置用量缓存失败: accountId={}", entry.getKey());
                }
            }

            results.putAll(fetched);
        }

        log.debug("用量预取完成: cacheHit={}, cacheMiss={}, total={}", cacheHit, cacheMiss, accountIds.size());

        return results;
    }

    /**
     * 获取单个账号的窗口用量
     */
    public double getWindowCost(Long accountId) {
        if (accountId == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.truncatedTo(ChronoUnit.HOURS);

        String cacheKey = getCacheKey(accountId, windowStart);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Double.parseDouble(cached);
            }
        } catch (Exception e) {
            log.warn("获取用量缓存失败: accountId={}", accountId);
        }

        // 缓存未命中，从数据库获取
        LocalDateTime windowEnd = windowStart.plusHours(1);
        Map<Long, Double> costs = fetchWindowCostsFromDB(List.of(accountId), windowStart, windowEnd);
        Double cost = costs.get(accountId);

        if (cost != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(cost), WINDOW_CACHE_TTL);
            } catch (Exception e) {
                log.warn("设置用量缓存失败: accountId={}", accountId);
            }
        }

        return cost != null ? cost : 0;
    }

    /**
     * 检查账号是否超过窗口配额
     */
    public boolean isOverWindowQuota(Long accountId, double quotaHours) {
        double currentCost = getWindowCost(accountId);
        return currentCost >= quotaHours;
    }

    /**
     * 清除账号的用量缓存
     */
    public void invalidateCache(Long accountId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.truncatedTo(ChronoUnit.HOURS);

        String cacheKey = getCacheKey(accountId, windowStart);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("清除用量缓存失败: accountId={}", accountId);
        }
    }

    /**
     * 从数据库批量获取窗口用量
     */
    private Map<Long, Double> fetchWindowCostsFromDB(List<Long> accountIds, LocalDateTime windowStart, LocalDateTime windowEnd) {
        Map<Long, Double> results = new HashMap<>();

        if (accountIds == null || accountIds.isEmpty()) {
            return results;
        }

        // 按账号分组查询用量
        for (Long accountId : accountIds) {
            try {
                // 查询该账号在窗口内的总用量
                // 这里简化处理，实际应该用聚合查询
                List<Object> usages = usageLogMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sub2api.module.billing.model.entity.UsageLog>()
                                .eq(com.sub2api.module.billing.model.entity.UsageLog::getAccountId, accountId)
                                .ge(com.sub2api.module.billing.model.entity.UsageLog::getCreatedAt, windowStart)
                                .lt(com.sub2api.module.billing.model.entity.UsageLog::getCreatedAt, windowEnd)
                );

                double totalCost = 0;
                for (Object obj : usages) {
                    if (obj instanceof com.sub2api.module.billing.model.entity.UsageLog usage) {
                        long totalTokens = (usage.getInputTokens() != null ? usage.getInputTokens() : 0)
                                + (usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
                        // 简单按 token 数量估算成本
                        totalCost += totalTokens * 0.000002;
                    }
                }

                results.put(accountId, totalCost);
            } catch (Exception e) {
                log.error("查询窗口用量失败: accountId={}, error={}", accountId, e.getMessage());
                results.put(accountId, 0.0);
            }
        }

        return results;
    }

    /**
     * 获取缓存键
     */
    private String getCacheKey(Long accountId, LocalDateTime windowStart) {
        return USAGE_WINDOW_KEY_PREFIX + accountId + ":" + windowStart.toString();
    }
}
