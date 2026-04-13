package com.sub2api.module.dashboard.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dashboard Hourly Aggregate Entity
 * 按小时聚合的用量统计
 */
@Data
@TableName("usage_dashboard_hourly")
public class UsageDashboardHourly {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 桶开始时间（整点）
     */
    private LocalDateTime bucketStart;

    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 输入 token 数
     */
    private Long inputTokens;

    /**
     * 输出 token 数
     */
    private Long outputTokens;

    /**
     * 缓存创建 token 数
     */
    private Long cacheCreationTokens;

    /**
     * 缓存读取 token 数
     */
    private Long cacheReadTokens;

    /**
     * 总成本
     */
    private Double totalCost;

    /**
     * 实际成本
     */
    private Double actualCost;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 活跃用户数
     */
    private Integer activeUsers;

    /**
     * 计算时间
     */
    private LocalDateTime computedAt;
}
