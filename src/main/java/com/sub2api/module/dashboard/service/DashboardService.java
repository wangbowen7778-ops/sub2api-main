package com.sub2api.module.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.dashboard.mapper.DashboardMapper;
import com.sub2api.module.dashboard.mapper.DashboardMapper.*;
import com.sub2api.module.dashboard.model.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Dashboard 统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dashboard.stats-cache-ttl-seconds:30}")
    private int statsCacheTtlSeconds;

    @Value("${dashboard.enabled:true}")
    private boolean dashboardEnabled;

    private static final String DASHBOARD_STATS_CACHE_KEY = "dashboard:stats";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 获取仪表盘统计
     */
    public DashboardStats getDashboardStats() {
        if (!dashboardEnabled) {
            return null;
        }

        // 尝试从缓存获取
        String cached = redisTemplate.opsForValue().get(DASHBOARD_STATS_CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardStats.class);
            } catch (Exception e) {
                log.warn("Failed to parse cached dashboard stats", e);
            }
        }

        // 缓存未命中，重新计算
        DashboardStats stats = calculateDashboardStats();

        // 保存到缓存
        try {
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(DASHBOARD_STATS_CACHE_KEY, json,
                    Duration.ofSeconds(statsCacheTtlSeconds));
        } catch (Exception e) {
            log.warn("Failed to cache dashboard stats", e);
        }

        return stats;
    }

    /**
     * 计算仪表盘统计
     */
    private DashboardStats calculateDashboardStats() {
        DashboardStats stats = new DashboardStats();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDateTime.of(now.toLocalDate(), LocalTime.MIN);
        LocalDateTime hourStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), 0));

        // 用户统计
        stats.setTotalUsers(dashboardMapper.countTotalUsers(todayStart));
        stats.setTodayNewUsers(dashboardMapper.countTodayNewUsers(todayStart));
        stats.setActiveUsers(dashboardMapper.countActiveUsers(todayStart));
        stats.setHourlyActiveUsers(dashboardMapper.countHourlyActiveUsers(hourStart));

        // API Key 统计
        stats.setTotalApiKeys(dashboardMapper.countTotalApiKeys());
        stats.setActiveApiKeys(dashboardMapper.countActiveApiKeys());

        // 账号统计
        stats.setTotalAccounts(dashboardMapper.countTotalAccounts());
        stats.setNormalAccounts(dashboardMapper.countNormalAccounts());
        stats.setErrorAccounts(dashboardMapper.countErrorAccounts());
        stats.setRateLimitAccounts(dashboardMapper.countRateLimitAccounts(now));
        stats.setOverloadAccounts(dashboardMapper.countOverloadAccounts(now));

        // 累计用量统计
        stats.setTotalRequests(dashboardMapper.countTotalRequests());
        stats.setTotalInputTokens(dashboardMapper.sumTotalInputTokens());
        stats.setTotalOutputTokens(dashboardMapper.sumTotalOutputTokens());
        stats.setTotalTokens(stats.getTotalInputTokens() + stats.getTotalOutputTokens());

        // 今日用量统计
        stats.setTodayRequests(dashboardMapper.countTodayRequests(todayStart));
        stats.setTodayInputTokens(dashboardMapper.sumTodayInputTokens(todayStart));
        stats.setTodayOutputTokens(dashboardMapper.sumTodayOutputTokens(todayStart));
        stats.setTodayTokens(stats.getTodayInputTokens() + stats.getTodayOutputTokens());

        // 计算成本
        calculateCosts(stats);

        // 统计新鲜度
        stats.setStatsUpdatedAt(now.format(DateTimeFormatter.ISO_DATE_TIME));
        stats.setStatsStale(false);

        return stats;
    }

    /**
     * 计算成本
     */
    private void calculateCosts(DashboardStats stats) {
        // TODO: 实际实现需要根据计费规则计算
        // 简单按 token 数量估算
        double inputPricePerToken = 0.000001;  // $0.001/1K input
        double outputPricePerToken = 0.000003; // $0.003/1K output

        stats.setTotalCost(stats.getTotalInputTokens() * inputPricePerToken +
                stats.getTotalOutputTokens() * outputPricePerToken);
        stats.setTotalActualCost(stats.getTotalCost());

        stats.setTodayCost(stats.getTodayInputTokens() * inputPricePerToken +
                stats.getTodayOutputTokens() * outputPricePerToken);
        stats.setTodayActualCost(stats.getTodayCost());
    }

    /**
     * 获取用量趋势
     */
    public List<TrendDataPoint> getUsageTrend(LocalDateTime startTime, LocalDateTime endTime,
                                              String granularity, Long userId, Long apiKeyId,
                                              Long accountId, Long groupId, String model) {
        List<UsageTrendRow> rows = dashboardMapper.selectUsageTrendByDay(startTime, endTime);

        return rows.stream()
                .map(row -> {
                    TrendDataPoint point = new TrendDataPoint();
                    point.setDate(row.getDate());
                    point.setRequests(row.getRequests());
                    point.setInputTokens(row.getInputTokens());
                    point.setOutputTokens(row.getOutputTokens());
                    point.setTotalTokens(row.getTotalTokens());
                    return point;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取模型统计
     */
    public List<ModelStat> getModelStats(LocalDateTime startTime, LocalDateTime endTime,
                                          Long userId, Long apiKeyId, Long accountId,
                                          Long groupId, String model) {
        List<ModelUsageRow> rows = dashboardMapper.selectModelUsageStats(startTime, endTime);

        return rows.stream()
                .map(row -> {
                    ModelStat stat = new ModelStat();
                    stat.setModel(row.getModel());
                    stat.setRequests(row.getRequests());
                    stat.setInputTokens(row.getInputTokens());
                    stat.setOutputTokens(row.getOutputTokens());
                    stat.setTotalTokens(row.getTotalTokens());
                    return stat;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取分组统计
     */
    public List<GroupStat> getGroupStats(LocalDateTime startTime, LocalDateTime endTime,
                                         Long userId, Long apiKeyId, Long accountId,
                                         Long groupId) {
        // TODO: 实现完整的分组统计查询
        return List.of();
    }

    /**
     * 获取分组用量摘要
     */
    public List<GroupUsageSummary> getGroupUsageSummary(LocalDateTime todayStart) {
        // TODO: 实现分组用量摘要
        return List.of();
    }

    /**
     * 获取用户用量趋势
     */
    public List<UserUsageTrendPoint> getUserUsageTrend(LocalDateTime startTime, LocalDateTime endTime,
                                                       String granularity, int limit) {
        // TODO: 实现用户用量趋势
        return List.of();
    }

    /**
     * 获取用户消费排名
     */
    public UserSpendingRankingResponse getUserSpendingRanking(LocalDateTime startTime,
                                                              LocalDateTime endTime, int limit) {
        // TODO: 实现用户消费排名
        UserSpendingRankingResponse response = new UserSpendingRankingResponse();
        response.setRanking(List.of());
        return response;
    }

    /**
     * 异步刷新仪表盘缓存
     */
    @Async
    public void refreshDashboardStatsAsync() {
        try {
            DashboardStats stats = calculateDashboardStats();
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(DASHBOARD_STATS_CACHE_KEY, json,
                    Duration.ofSeconds(statsCacheTtlSeconds));
            log.debug("Dashboard stats cache refreshed");
        } catch (Exception e) {
            log.error("Failed to refresh dashboard stats cache", e);
        }
    }

    /**
     * 使仪表盘缓存失效
     */
    public void invalidateCache() {
        redisTemplate.delete(DASHBOARD_STATS_CACHE_KEY);
    }
}
