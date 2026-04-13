package com.sub2api.module.dashboard.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dashboard Aggregation Configuration
 * 仪表盘预聚合配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "dashboard.aggregation")
public class DashboardAggregationConfig {

    /**
     * 是否启用预聚合作业
     */
    private boolean enabled = true;

    /**
     * 聚合刷新间隔（秒）
     */
    private int intervalSeconds = 60;

    /**
     * 回看窗口（秒）
     */
    private int lookbackSeconds = 300;

    /**
     * 是否允许全量回填
     */
    private boolean backfillEnabled = true;

    /**
     * 回填最大跨度（天）
     */
    private int backfillMaxDays = 30;

    /**
     * 启动时重算最近 N 天
     */
    private int recomputeDays = 0;

    /**
     * 保留窗口配置
     */
    private RetentionConfig retention = new RetentionConfig();

    @Data
    public static class RetentionConfig {
        /**
         * usage_logs 保留天数
         */
        private int usageLogsDays = 7;

        /**
         * usage_billing_dedup 保留天数
         */
        private int usageBillingDedupDays = 30;

        /**
         * hourly 聚合表保留天数
         */
        private int hourlyDays = 7;

        /**
         * daily 聚合表保留天数
         */
        private int dailyDays = 90;
    }
}
