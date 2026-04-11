package com.sub2api.module.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.ops.mapper.OpsErrorLogMapper;
import com.sub2api.module.ops.model.entity.OpsErrorLog;
import com.sub2api.module.ops.model.vo.OpsDashboardOverview;
import com.sub2api.module.ops.model.vo.OpsDashboardOverview.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ops 监控服务
 * 提供错误日志记录、仪表板概览、系统指标等监控功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsService {

    private final OpsErrorLogMapper errorLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${ops.enabled:true}")
    private boolean opsEnabled;

    /**
     * 记录错误日志
     */
    public void recordError(OpsErrorLog entry) {
        if (!opsEnabled) {
            log.debug("Ops monitoring is disabled, skipping error logging");
            return;
        }

        try {
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(LocalDateTime.now());
            }
            errorLogMapper.insert(entry);
        } catch (Exception e) {
            // Never bubble up to gateway; best-effort logging
            log.error("Failed to record error log: {}", e.getMessage());
        }
    }

    /**
     * 批量记录错误日志
     */
    public void recordErrorBatch(List<OpsErrorLog> entries) {
        if (!opsEnabled || entries == null || entries.isEmpty()) {
            return;
        }

        try {
            for (OpsErrorLog entry : entries) {
                if (entry.getCreatedAt() == null) {
                    entry.setCreatedAt(LocalDateTime.now());
                }
            }
            errorLogMapper.insertBatch(entries);
        } catch (Exception e) {
            log.error("Failed to batch record error logs: {}", e.getMessage());
        }
    }

    /**
     * 获取仪表板概览
     */
    public OpsDashboardOverview getDashboardOverview(OpsDashboardFilter filter) {
        if (!opsEnabled) {
            return null;
        }

        // TODO: 实现完整的仪表板聚合查询
        // 目前返回基础统计
        OpsDashboardOverview overview = new OpsDashboardOverview();
        overview.setStartTime(filter.getStartTime());
        overview.setEndTime(filter.getEndTime());

        // 计算错误统计
        ErrorStats errorStats = calculateErrorStats(filter);
        overview.setErrorStats(errorStats);

        // 计算健康评分
        overview.setHealthScore(calculateHealthScore(overview));

        return overview;
    }

    /**
     * 计算错误统计
     */
    private ErrorStats calculateErrorStats(OpsDashboardFilter filter) {
        ErrorStats stats = new ErrorStats();

        List<OpsErrorLog> errors = errorLogMapper.selectByTimeRange(
                filter.getStartTime(), filter.getEndTime());

        stats.setTotalErrors(errors.size());

        // 按错误类型分组统计
        long rateLimitErrors = 0;
        long authErrors = 0;
        long upstreamErrors = 0;
        long timeoutErrors = 0;

        for (OpsErrorLog error : errors) {
            if (error.getStatusCode() != null) {
                switch (error.getStatusCode()) {
                    case 429 -> rateLimitErrors++;
                    case 401, 403 -> authErrors++;
                    case 504, 598 -> timeoutErrors++;
                    default -> {
                        if (error.getStatusCode() >= 500) {
                            upstreamErrors++;
                        }
                    }
                }
            }
        }

        stats.setRateLimitErrors(rateLimitErrors);
        stats.setAuthErrors(authErrors);
        stats.setUpstreamErrors(upstreamErrors);
        stats.setTimeoutErrors(timeoutErrors);

        return stats;
    }

    /**
     * 计算健康评分 (0-100)
     */
    private int calculateHealthScore(OpsDashboardOverview overview) {
        if (overview.getErrorStats() == null) {
            return 100;
        }

        long totalErrors = overview.getErrorStats().getTotalErrors();
        if (totalErrors == 0) {
            return 100;
        }

        // 简单算法：错误率越高，分数越低
        double errorRate = (double) totalErrors / 1000; // 假设基准为1000请求
        if (errorRate > 0.5) {
            return 10;
        } else if (errorRate > 0.2) {
            return 30;
        } else if (errorRate > 0.1) {
            return 50;
        } else if (errorRate > 0.05) {
            return 70;
        } else if (errorRate > 0.01) {
            return 90;
        }
        return 100;
    }

    /**
     * 获取最新的系统指标
     */
    public SystemMetrics getLatestSystemMetrics(int limit) {
        // TODO: 实现系统指标获取
        SystemMetrics metrics = new SystemMetrics();
        metrics.setCollectedAt(LocalDateTime.now());
        return metrics;
    }

    /**
     * 获取任务心跳列表
     */
    public List<JobHeartbeat> getJobHeartbeats() {
        // TODO: 实现任务心跳获取
        return List.of();
    }

    /**
     * 检查监控是否启用
     */
    public boolean isMonitoringEnabled() {
        return opsEnabled;
    }

    /**
     * 仪表板过滤器
     */
    @lombok.Data
    public static class OpsDashboardFilter {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String platform;
        private Long groupId;
        private String queryMode; // raw, preagg, auto
    }
}
