package com.sub2api.module.dashboard.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 仪表盘统计
 */
@Data
@Accessors(chain = true)
public class DashboardStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 用户统计 ==========
    private long totalUsers;
    private long todayNewUsers;
    private long activeUsers;
    private long hourlyActiveUsers;

    // ========== 预聚合新鲜度 ==========
    private String statsUpdatedAt;
    private boolean statsStale;

    // ========== API Key 统计 ==========
    private long totalApiKeys;
    private long activeApiKeys;

    // ========== 账户统计 ==========
    private long totalAccounts;
    private long normalAccounts;
    private long errorAccounts;
    private long rateLimitAccounts;
    private long overloadAccounts;

    // ========== 累计 Token 使用统计 ==========
    private long totalRequests;
    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalCacheCreationTokens;
    private long totalCacheReadTokens;
    private long totalTokens;
    private double totalCost;
    private double totalActualCost;

    // ========== 今日 Token 使用统计 ==========
    private long todayRequests;
    private long todayInputTokens;
    private long todayOutputTokens;
    private long todayCacheCreationTokens;
    private long todayCacheReadTokens;
    private long todayTokens;
    private double todayCost;
    private double todayActualCost;

    // ========== 系统运行统计 ==========
    private double averageDurationMs;

    // ========== 性能指标 ==========
    private long rpm;
    private long tpm;
}
