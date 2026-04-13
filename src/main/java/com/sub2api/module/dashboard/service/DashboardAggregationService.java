package com.sub2api.module.dashboard.service;

import com.sub2api.module.dashboard.mapper.DashboardAggregationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dashboard Aggregation Service
 * 仪表盘预聚合服务 - 负责定时聚合与回填
 *
 * 功能：
 * 1. 定时聚合 usage_logs 到 hour/daily 聚合表
 * 2. 支持全量回填历史数据
 * 3. 支持重新计算指定范围（用于数据修复）
 * 4. 自动清理过期数据（保留策略）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardAggregationService {

    private final DashboardAggregationMapper aggregationMapper;
    private final DashboardAggregationConfig config;

    @Value("${dashboard.timezone:Asia/Shanghai}")
    private String timezone;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<LocalDateTime> lastRetentionCleanup = new AtomicReference<>();

    private static final int USAGE_LOGS_CLEANUP_BATCH_SIZE = 10000;
    private static final long AGGREGATION_TIMEOUT_SECONDS = 120;
    private static final long BACKFILL_TIMEOUT_SECONDS = 1800;
    private static final long RETENTION_CLEANUP_INTERVAL_HOURS = 6;

    private ZoneId zoneId;

    @PostConstruct
    public void init() {
        try {
            this.zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            this.zoneId = ZoneId.systemDefault();
            log.warn("Failed to parse timezone '{}', using system default: {}", timezone, e.getMessage());
        }

        if (config.isEnabled()) {
            log.info("DashboardAggregationService initialized with timezone={}, interval={}s, lookback={}s",
                    zoneId, config.getIntervalSeconds(), config.getLookbackSeconds());

            if (config.getRecomputeDays() > 0) {
                recomputeRecentDays();
            }
        } else {
            log.info("DashboardAggregationService is disabled");
        }
    }

    /**
     * 主调度方法 - 由 Spring @Scheduled 驱动
     */
    @Scheduled(fixedRate = 60000)
    public void runScheduledAggregation() {
        if (!config.isEnabled()) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Aggregation job is already running, skipping");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now(zoneId);
            LocalDateTime lastWatermark = getLastWatermark();
            LocalDateTime start;

            if (lastWatermark == null) {
                // 首次运行，使用保留窗口作为起始点
                int retentionDays = config.getRetention().getUsageLogsDays();
                if (retentionDays <= 0) {
                    retentionDays = 1;
                }
                start = truncateToDay(now.minusDays(retentionDays));
            } else {
                // 回看窗口内的数据
                start = lastWatermark.minusSeconds(config.getLookbackSeconds());
                if (start.isAfter(now)) {
                    start = now.minusSeconds(config.getLookbackSeconds());
                }
            }

            if (aggregateRange(start, now)) {
                updateWatermark(now);
            }

            maybeCleanupRetention(now);

        } catch (Exception e) {
            log.error("Scheduled aggregation failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    /**
     * 触发回填（异步）
     */
    public void triggerBackfill(LocalDateTime start, LocalDateTime end) {
        if (!config.isBackfillEnabled()) {
            log.warn("Backfill is disabled");
            return;
        }

        if (!end.isAfter(start)) {
            log.warn("Invalid backfill range: start={}, end={}", start, end);
            return;
        }

        if (config.getBackfillMaxDays() > 0) {
            long daysBetween = java.time.Duration.between(start, end).toDays();
            if (daysBetween > config.getBackfillMaxDays()) {
                log.warn("Backfill range too large: {} days > max {} days", daysBetween, config.getBackfillMaxDays());
                return;
            }
        }

        final LocalDateTime startUTC = start, endUTC = end;
        Thread.ofVirtual().start(() -> {
            try {
                backfillRange(startUTC, endUTC);
            } catch (Exception e) {
                log.error("Backfill failed: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 触发重新计算（异步）
     */
    public void triggerRecomputeRange(LocalDateTime start, LocalDateTime end) {
        if (!config.isEnabled()) {
            log.warn("Aggregation service is disabled");
            return;
        }

        if (!end.isAfter(start)) {
            log.warn("Invalid recompute range: start={}, end={}", start, end);
            return;
        }

        final LocalDateTime startUTC = start, endUTC = end;
        Thread.ofVirtual().start(() -> {
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    recomputeRange(startUTC, endUTC);
                    return;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("running")) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        log.error("Recompute failed: {}", e.getMessage());
                        return;
                    }
                }
            }
            log.error("Recompute gave up after {} retries", maxRetries);
        });
    }

    /**
     * 执行回填
     */
    private void backfillRange(LocalDateTime start, LocalDateTime end) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Aggregation job is already running, backfill rejected");
            return;
        }

        try {
            log.info("Starting backfill: start={}, end={}", start, end);
            LocalDateTime cursor = truncateToDay(start);

            while (cursor.isBefore(end)) {
                LocalDateTime windowEnd = cursor.plusDays(1);
                if (windowEnd.isAfter(end)) {
                    windowEnd = end;
                }

                if (aggregateRange(cursor, windowEnd)) {
                    updateWatermark(windowEnd);
                }

                cursor = windowEnd;
            }

            log.info("Backfill completed: start={}, end={}", start, end);

        } finally {
            running.set(false);
        }
    }

    /**
     * 执行重新计算（清空范围内数据后重建）
     */
    private void recomputeRange(LocalDateTime start, LocalDateTime end) {
        if (!running.compareAndSet(false, true)) {
            throw new RuntimeException("aggregation job is already running");
        }

        try {
            log.info("Starting recompute: start={}, end={}", start, end);

            // 先清空范围内数据
            aggregationMapper.deleteHourlyRange(start, end);
            aggregationMapper.deleteHourlyUsersRange(start, end);
            aggregationMapper.deleteDailyRange(start.toLocalDate(), end.toLocalDate());
            aggregationMapper.deleteDailyUsersRange(start.toLocalDate(), end.toLocalDate());

            // 重建
            aggregateRange(start, end);

            log.info("Recompute completed: start={}, end={}", start, end);

        } finally {
            running.set(false);
        }
    }

    /**
     * 执行聚合
     */
    private boolean aggregateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            return false;
        }

        try {
            // 确保分区存在
            ensureUsageLogsPartitions(end);

            // 聚合到小时桶
            LocalDateTime hourStart = truncateToHour(start, zoneId);
            LocalDateTime hourEnd = truncateToHour(end, zoneId);
            if (end.isAfter(hourEnd)) {
                hourEnd = hourEnd.plusHours(1);
            }

            // 聚合到天桶
            LocalDate dayStart = truncateToDay(start.toLocalDate());
            LocalDate dayEnd = truncateToDay(end.toLocalDate());
            if (end.toLocalDate().isAfter(dayEnd)) {
                dayEnd = dayEnd.plusDays(1);
            }

            // 插入小时活跃用户
            int hourlyUsers = aggregationMapper.insertHourlyActiveUsers(hourStart, hourEnd, timezone);
            log.debug("Inserted {} hourly active users", hourlyUsers);

            // 插入天活跃用户
            int dailyUsers = aggregationMapper.insertDailyActiveUsers(hourStart, hourEnd, timezone);
            log.debug("Inserted {} daily active users", dailyUsers);

            // Upsert 小时聚合
            int hourlyAgg = aggregationMapper.upsertHourlyAggregates(hourStart, hourEnd, timezone);
            log.debug("Upserted {} hourly aggregates", hourlyAgg);

            // Upsert 天聚合
            int dailyAgg = aggregationMapper.upsertDailyAggregates(hourStart, hourEnd, dayStart, dayEnd, timezone);
            log.debug("Upserted {} daily aggregates", dailyAgg);

            return true;

        } catch (Exception e) {
            log.error("Aggregate range failed: start={}, end={}, error={}", start, end, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取上次水位
     */
    private LocalDateTime getLastWatermark() {
        try {
            return aggregationMapper.getAggregationWatermark();
        } catch (Exception e) {
            log.warn("Failed to get watermark: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新水位
     */
    private void updateWatermark(LocalDateTime aggregatedAt) {
        try {
            aggregationMapper.updateAggregationWatermark(aggregatedAt);
            log.debug("Updated watermark to {}", aggregatedAt);
        } catch (Exception e) {
            log.error("Failed to update watermark: {}", e.getMessage());
        }
    }

    /**
     * 保留清理（定期执行）
     */
    private void maybeCleanupRetention(LocalDateTime now) {
        LocalDateTime last = lastRetentionCleanup.get();
        if (last != null) {
            long hoursSince = java.time.Duration.between(last, now).toHours();
            if (hoursSince < RETENTION_CLEANUP_INTERVAL_HOURS) {
                return;
            }
        }

        try {
            LocalDateTime hourlyCutoff = now.minusDays(config.getRetention().getHourlyDays());
            LocalDate dailyCutoff = now.minusDays(config.getRetention().getDailyDays());
            LocalDateTime usageCutoff = now.minusDays(config.getRetention().getUsageLogsDays());

            // 清理聚合表
            aggregationMapper.cleanupHourlyAggregates(hourlyCutoff);
            aggregationMapper.cleanupHourlyUsers(hourlyCutoff);
            aggregationMapper.cleanupDailyAggregates(dailyCutoff);
            aggregationMapper.cleanupDailyUsers(dailyCutoff);

            // 清理 usage_logs
            cleanupUsageLogs(usageCutoff);

            lastRetentionCleanup.set(now);
            log.info("Retention cleanup completed: hourlyCutoff={}, dailyCutoff={}, usageCutoff={}",
                    hourlyCutoff, dailyCutoff, usageCutoff);

        } catch (Exception e) {
            log.error("Retention cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 清理 usage_logs（分区表直接 DROP，分区表则分批 DELETE）
     */
    private void cleanupUsageLogs(LocalDateTime cutoff) {
        try {
            if (aggregationMapper.isUsageLogsPartitioned()) {
                cleanupUsageLogsPartitions(cutoff);
            } else {
                cleanupUsageLogsBatch(cutoff);
            }
        } catch (Exception e) {
            log.error("Cleanup usage_logs failed: {}", e.getMessage());
        }
    }

    /**
     * 分区表：删除旧分区
     */
    private void cleanupUsageLogsPartitions(LocalDateTime cutoff) {
        try {
            List<String> partitions = aggregationMapper.getUsageLogsPartitions();
            LocalDate cutoffMonth = LocalDate.from(cutoff.atStartOfDay(zoneId)).toLocalDate().withDayOfMonth(1);

            for (String partition : partitions) {
                if (!partition.startsWith("usage_logs_")) {
                    continue;
                }

                String suffix = partition.substring("usage_logs_".length());
                try {
                    LocalDate partitionMonth = LocalDate.parse(suffix, DateTimeFormatter.ofPattern("yyyyMM"));
                    if (partitionMonth.isBefore(cutoffMonth)) {
                        aggregationMapper.dropPartition(partition);
                        log.info("Dropped partition: {}", partition);
                    }
                } catch (Exception e) {
                    // 忽略解析失败的分区名
                }
            }
        } catch (Exception e) {
            log.error("Cleanup partitions failed: {}", e.getMessage());
        }
    }

    /**
     * 非分区表：分批 DELETE
     */
    private void cleanupUsageLogsBatch(LocalDateTime cutoff) {
        try {
            int deleted;
            do {
                deleted = aggregationMapper.deleteUsageLogsBatch(cutoff, USAGE_LOGS_CLEANUP_BATCH_SIZE);
                if (deleted > 0) {
                    log.debug("Deleted {} usage_logs batch", deleted);
                }
            } while (deleted >= USAGE_LOGS_CLEANUP_BATCH_SIZE);
        } catch (Exception e) {
            log.error("Batch cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 确保 usage_logs 分区存在
     */
    private void ensureUsageLogsPartitions(LocalDateTime now) {
        try {
            if (!aggregationMapper.isUsageLogsPartitioned()) {
                return;
            }

            LocalDateTime monthStart = truncateToMonth(now);
            LocalDateTime prevMonth = monthStart.minusMonths(1);
            LocalDateTime nextMonth = monthStart.plusMonths(1);

            for (LocalDateTime month : List.of(prevMonth, monthStart, nextMonth)) {
                ensureUsageLogsPartition(month);
            }
        } catch (Exception e) {
            log.warn("Ensure partitions failed: {}", e.getMessage());
        }
    }

    /**
     * 确保指定月份的分区存在
     */
    private void ensureUsageLogsPartition(LocalDateTime month) {
        try {
            String partitionName = "usage_logs_" + month.format(DateTimeFormatter.ofPattern("yyyyMM"));
            // 分区由 DB 自动管理，这里仅做预防性检查
            log.debug("Partition check for {}", partitionName);
        } catch (Exception e) {
            log.warn("Ensure partition failed for month {}: {}", month, e.getMessage());
        }
    }

    /**
     * 启动时重算最近 N 天
     */
    private void recomputeRecentDays() {
        int days = config.getRecomputeDays();
        if (days <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime start = now.minusDays(days);

        log.info("Starting recompute for recent {} days: {} to {}", days, start, now);

        try {
            backfillRange(start, now);
        } catch (Exception e) {
            log.error("Startup recompute failed: {}", e.getMessage());
        }
    }

    // ========== 工具方法 ==========

    private LocalDateTime truncateToDay(LocalDateTime t) {
        return t.toLocalDate().atStartOfDay();
    }

    private LocalDateTime truncateToHour(LocalDateTime t, ZoneId zone) {
        return t.withMinute(0).withSecond(0).withNano(0);
    }

    private LocalDateTime truncateToMonth(LocalDateTime t) {
        return t.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private LocalDate truncateToDay(LocalDate d) {
        return d;
    }

    /**
     * 获取服务状态
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "enabled", config.isEnabled(),
                "timezone", zoneId.toString(),
                "interval_seconds", config.getIntervalSeconds(),
                "lookback_seconds", config.getLookbackSeconds(),
                "running", running.get(),
                "last_retention_cleanup", lastRetentionCleanup.get() != null
                        ? lastRetentionCleanup.get().toString() : null
        );
    }
}
