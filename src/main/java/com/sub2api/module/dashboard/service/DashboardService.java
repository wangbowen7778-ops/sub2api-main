package com.sub2api.module.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.dashboard.mapper.DashboardMapper;
import com.sub2api.module.dashboard.mapper.DashboardMapper.*;
import com.sub2api.module.dashboard.model.vo.*;
import com.sub2api.module.billing.service.BillingCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final BillingCalculator billingCalculator;

    @Value("${dashboard.stats-cache-ttl-seconds:30}")
    private int statsCacheTtlSeconds;

    @Value("${dashboard.enabled:true}")
    private boolean dashboardEnabled;

    private static final String DASHBOARD_STATS_CACHE_KEY = "dashboard:stats";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    // 默认费率 (每 1M tokens 的价格，单位：元)
    private static final BigDecimal DEFAULT_INPUT_RATE = new BigDecimal("0.000001");
    private static final BigDecimal DEFAULT_OUTPUT_RATE = new BigDecimal("0.000003");

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

        // 累计缓存 token 统计
        stats.setTotalCacheCreationTokens(dashboardMapper.sumTotalCacheCreationTokens());
        stats.setTotalCacheReadTokens(dashboardMapper.sumTotalCacheReadTokens());

        // 今日用量统计
        stats.setTodayRequests(dashboardMapper.countTodayRequests(todayStart));
        stats.setTodayInputTokens(dashboardMapper.sumTodayInputTokens(todayStart));
        stats.setTodayOutputTokens(dashboardMapper.sumTodayOutputTokens(todayStart));
        stats.setTodayTokens(stats.getTodayInputTokens() + stats.getTodayOutputTokens());

        // 今日缓存 token 统计
        stats.setTodayCacheCreationTokens(dashboardMapper.sumTodayCacheCreationTokens(todayStart));
        stats.setTodayCacheReadTokens(dashboardMapper.sumTodayCacheReadTokens(todayStart));

        // 计算成本（优先使用数据库中的实际成本）
        calculateCosts(stats, todayStart);

        // 统计新鲜度
        stats.setStatsUpdatedAt(now.format(DateTimeFormatter.ISO_DATE_TIME));
        stats.setStatsStale(false);

        return stats;
    }

    /**
     * 计算成本
     * 优先使用数据库中的实际成本，必要时使用费率估算
     */
    private void calculateCosts(DashboardStats stats, LocalDateTime todayStart) {
        // 获取数据库中存储的实际成本
        double dbTotalCost = dashboardMapper.sumTotalCost();
        double dbTotalActualCost = dashboardMapper.sumTotalActualCost();
        double dbTodayCost = dashboardMapper.sumTodayCost(todayStart);
        double dbTodayActualCost = dashboardMapper.sumTodayActualCost(todayStart);

        // 如果数据库成本有效（约大于0），使用数据库值；否则使用估算
        if (dbTotalCost > 0) {
            stats.setTotalCost(dbTotalCost);
        } else {
            stats.setTotalCost(estimateCost(stats.getTotalInputTokens(), stats.getTotalOutputTokens()));
        }

        if (dbTotalActualCost > 0) {
            stats.setTotalActualCost(dbTotalActualCost);
        } else {
            stats.setTotalActualCost(stats.getTotalCost());
        }

        if (dbTodayCost > 0) {
            stats.setTodayCost(dbTodayCost);
        } else {
            stats.setTodayCost(estimateCost(stats.getTodayInputTokens(), stats.getTodayOutputTokens()));
        }

        if (dbTodayActualCost > 0) {
            stats.setTodayActualCost(dbTodayActualCost);
        } else {
            stats.setTodayActualCost(stats.getTodayCost());
        }
    }

    /**
     * 使用默认费率估算成本
     */
    private double estimateCost(long inputTokens, long outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP)
                .multiply(DEFAULT_INPUT_RATE);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP)
                .multiply(DEFAULT_OUTPUT_RATE);
        return inputCost.add(outputCost).doubleValue();
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
        List<DashboardMapper.GroupUsageRow> rows = dashboardMapper.selectGroupUsageStats(startTime, endTime);

        return rows.stream()
                .map(row -> {
                    GroupStat stat = new GroupStat();
                    stat.setGroupId(row.getGroupId());
                    stat.setGroupName(row.getGroupName());
                    stat.setRequests(row.getRequests());
                    stat.setTotalTokens(row.getTotalTokens());
                    stat.setCost(row.getTotalTokens() * 0.000002); // 估算价格
                    return stat;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取分组用量摘要
     */
    public List<GroupUsageSummary> getGroupUsageSummary(LocalDateTime todayStart) {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardMapper.GroupUsageRow> todayStats = dashboardMapper.selectGroupUsageStats(todayStart, now);
        List<DashboardMapper.GroupUsageRow> allStats = dashboardMapper.selectGroupUsageStats(
                LocalDateTime.of(2020, 1, 1, 0, 0), now);

        return todayStats.stream()
                .map(row -> {
                    GroupUsageSummary summary = new GroupUsageSummary();
                    summary.setGroupId(row.getGroupId());
                    summary.setTodayCost(row.getTotalTokens() * 0.000002);

                    // 计算累计成本
                    var allTimeRow = allStats.stream()
                            .filter(r -> row.getGroupId().equals(r.getGroupId()))
                            .findFirst()
                            .orElse(null);
                    long allTimeTokens = allTimeRow != null ? allTimeRow.getTotalTokens() : 0;
                    summary.setTotalCost(allTimeTokens * 0.000002);

                    return summary;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户用量趋势
     */
    public List<UserUsageTrendPoint> getUserUsageTrend(LocalDateTime startTime, LocalDateTime endTime,
                                                       String granularity, int limit) {
        List<DashboardMapper.UserUsageTrendRow> rows = dashboardMapper.selectUserUsageTrend(
                startTime, endTime, limit > 0 ? limit : 100);

        return rows.stream()
                .map(row -> {
                    UserUsageTrendPoint point = new UserUsageTrendPoint();
                    point.setDate(row.getDate());
                    point.setUserId(row.getUserId());
                    point.setEmail(row.getEmail());
                    point.setRequests(row.getRequests());
                    point.setInputTokens(row.getInputTokens());
                    point.setOutputTokens(row.getOutputTokens());
                    point.setTotalTokens(row.getTotalTokens());
                    return point;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户消费排名
     */
    public UserSpendingRankingResponse getUserSpendingRanking(LocalDateTime startTime,
                                                              LocalDateTime endTime, int limit) {
        List<DashboardMapper.UserSpendingRow> rows = dashboardMapper.selectUserSpendingRanking(
                startTime, endTime, limit > 0 ? limit : 50);

        UserSpendingRankingResponse response = new UserSpendingRankingResponse();

        List<UserSpendingRankingResponse.UserSpendingRankingItem> ranking = rows.stream()
                .map(row -> {
                    UserSpendingRankingResponse.UserSpendingRankingItem item =
                            new UserSpendingRankingResponse.UserSpendingRankingItem();
                    item.setUserId(row.getUserId());
                    item.setEmail(row.getEmail());
                    item.setRequests(row.getRequests());
                    item.setTokens(row.getTotalTokens());
                    item.setActualCost(row.getCost());
                    return item;
                })
                .collect(Collectors.toList());

        response.setRanking(ranking);
        response.setTotalRequests(ranking.stream().mapToLong(UserSpendingRankingResponse.UserSpendingRankingItem::getRequests).sum());
        response.setTotalTokens(ranking.stream().mapToLong(UserSpendingRankingResponse.UserSpendingRankingItem::getTokens).sum());
        response.setTotalActualCost(ranking.stream().mapToDouble(UserSpendingRankingResponse.UserSpendingRankingItem::getActualCost).sum());

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
