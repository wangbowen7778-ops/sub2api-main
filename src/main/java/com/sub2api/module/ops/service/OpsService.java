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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final SystemMetricsService systemMetricsService;

    @Value("${ops.enabled:true}")
    private boolean opsEnabled;

    // 任务心跳追踪 (简单内存实现，生产环境可考虑 Redis)
    private final Map<String, JobHeartbeat> jobHeartbeats = new ConcurrentHashMap<>();

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
                errorLogMapper.insert(entry);
            }
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

        OpsDashboardOverview overview = new OpsDashboardOverview();
        overview.setStartTime(filter.getStartTime());
        overview.setEndTime(filter.getEndTime());
        overview.setQueryMode(filter.getQueryMode() != null ? filter.getQueryMode() : "auto");

        // 计算错误统计
        ErrorStats errorStats = calculateErrorStats(filter);
        overview.setErrorStats(errorStats);

        // 设置请求统计（如果有 usage_logs 数据源可以扩展）
        overview.setRequestStats(calculateRequestStats(filter));

        // 计算健康评分
        overview.setHealthScore(calculateHealthScore(overview));

        // 获取系统指标
        overview.setSystemMetrics(getLatestSystemMetrics(1));

        // 获取任务心跳
        overview.setJobHeartbeats(getJobHeartbeats());

        return overview;
    }

    /**
     * 计算错误统计
     */
    private ErrorStats calculateErrorStats(OpsDashboardFilter filter) {
        ErrorStats stats = new ErrorStats();

        // 获取错误日志列表
        List<OpsErrorLog> errors = selectErrorsByFilter(filter);

        stats.setTotalErrors(errors.size());

        // 按错误类型分组统计
        long rateLimitErrors = 0;
        long authErrors = 0;
        long upstreamErrors = 0;
        long timeoutErrors = 0;
        long otherErrors = 0;

        // 用于错误码和错误阶段统计
        Map<String, Long> errorsByCode = new ConcurrentHashMap<>();
        Map<String, Long> errorsByPhase = new ConcurrentHashMap<>();

        for (OpsErrorLog error : errors) {
            // 按状态码分类
            if (error.getStatusCode() != null) {
                String codeKey = String.valueOf(error.getStatusCode());
                errorsByCode.merge(codeKey, 1L, Long::sum);

                switch (error.getStatusCode()) {
                    case 429 -> rateLimitErrors++;
                    case 401, 403 -> authErrors++;
                    case 504, 598 -> timeoutErrors++;
                    default -> {
                        if (error.getStatusCode() >= 500) {
                            upstreamErrors++;
                        } else {
                            otherErrors++;
                        }
                    }
                }
            } else {
                otherErrors++;
            }

            // 按错误阶段分类
            if (error.getErrorPhase() != null) {
                errorsByPhase.merge(error.getErrorPhase(), 1L, Long::sum);
            }
        }

        stats.setRateLimitErrors(rateLimitErrors);
        stats.setAuthErrors(authErrors);
        stats.setUpstreamErrors(upstreamErrors);
        stats.setTimeoutErrors(timeoutErrors);
        stats.setOtherErrors(otherErrors);
        stats.setErrorsByCode(errorsByCode);
        stats.setErrorsByPhase(errorsByPhase);

        return stats;
    }

    /**
     * 根据过滤器查询错误日志
     */
    private List<OpsErrorLog> selectErrorsByFilter(OpsDashboardFilter filter) {
        // 如果有平台过滤
        if (filter.getPlatform() != null && !filter.getPlatform().isBlank()) {
            return errorLogMapper.selectByTimeRangeAndPlatform(
                    filter.getStartTime(), filter.getEndTime(), filter.getPlatform());
        }
        // 如果有分组过滤
        if (filter.getGroupId() != null) {
            return errorLogMapper.selectByTimeRangeAndGroupId(
                    filter.getStartTime(), filter.getEndTime(), filter.getGroupId());
        }
        // 默认查询所有
        return errorLogMapper.selectByTimeRange(filter.getStartTime(), filter.getEndTime());
    }

    /**
     * 计算请求统计（基础实现）
     */
    private RequestStats calculateRequestStats(OpsDashboardFilter filter) {
        RequestStats requestStats = new RequestStats();

        // 获取错误数量作为请求量的参考
        long errorCount = errorLogMapper.countByTimeRange(filter.getStartTime(), filter.getEndTime());

        // 假设错误率不超过一定比例来估算总请求
        // 实际实现应该查询 usage_logs 表
        requestStats.setTotalRequests(errorCount * 10); // 估算值
        requestStats.setFailedRequests(errorCount);
        requestStats.setSuccessfulRequests(requestStats.getTotalRequests() - errorCount);

        if (requestStats.getTotalRequests() > 0) {
            requestStats.setSuccessRate(
                    (double) requestStats.getSuccessfulRequests() / requestStats.getTotalRequests() * 100);
        } else {
            requestStats.setSuccessRate(100.0);
        }

        requestStats.setAvgLatencyMs(0);
        requestStats.setP99LatencyMs(0);

        return requestStats;
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
        if (!opsEnabled) {
            return null;
        }
        try {
            // 使用 SystemMetricsService 获取 JVM/系统指标
            SystemMetricsService.SystemMetrics jvmMetrics = systemMetricsService.getMetrics();

            // 转换为 OpsDashboardOverview.SystemMetrics
            SystemMetrics metrics = new SystemMetrics();
            metrics.setCollectedAt(LocalDateTime.now());

            // 设置 CPU 和内存使用率
            if (jvmMetrics.getCpu() != null) {
                metrics.setCpuUsage(jvmMetrics.getCpu().getProcessCpuUsage());
            }
            if (jvmMetrics.getMemory() != null) {
                metrics.setMemoryUsage(jvmMetrics.getMemory().getUsagePercent());
            }

            return metrics;
        } catch (Exception e) {
            log.warn("Failed to get system metrics: {}", e.getMessage());
            SystemMetrics metrics = new SystemMetrics();
            metrics.setCollectedAt(LocalDateTime.now());
            return metrics;
        }
    }

    /**
     * 获取任务心跳列表
     */
    public List<JobHeartbeat> getJobHeartbeats() {
        if (!opsEnabled) {
            return List.of();
        }
        // 清理过期的任务心跳（超过5分钟未更新的）
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        jobHeartbeats.entrySet().removeIf(entry -> {
            JobHeartbeat hb = entry.getValue();
            return hb.getLastHeartbeat() != null && hb.getLastHeartbeat().isBefore(cutoff);
        });
        return List.copyOf(jobHeartbeats.values());
    }

    /**
     * 更新任务心跳
     */
    public void updateJobHeartbeat(String jobName, String status, String message) {
        JobHeartbeat heartbeat = jobHeartbeats.computeIfAbsent(jobName, k -> new JobHeartbeat());
        heartbeat.setJobName(jobName);
        heartbeat.setStatus(status);
        heartbeat.setMessage(message);
        heartbeat.setLastHeartbeat(LocalDateTime.now());
        log.debug("Updated job heartbeat: job={}, status={}", jobName, status);
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

    /**
     * 错误日志过滤器
     */
    @lombok.Data
    public static class OpsErrorLogFilter {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String platform;
        private Long groupId;
        private Integer page;
        private Integer pageSize;
    }

    /**
     * 获取错误日志列表
     */
    public List<OpsErrorLog> getErrorLogs(OpsErrorLogFilter filter) {
        if (filter == null) {
            return List.of();
        }
        try {
            List<OpsErrorLog> errors;
            if (filter.getPlatform() != null && !filter.getPlatform().isBlank()) {
                errors = errorLogMapper.selectByTimeRangeAndPlatform(
                        filter.getStartTime(), filter.getEndTime(), filter.getPlatform());
            } else if (filter.getGroupId() != null) {
                errors = errorLogMapper.selectByTimeRangeAndGroupId(
                        filter.getStartTime(), filter.getEndTime(), filter.getGroupId());
            } else {
                errors = errorLogMapper.selectByTimeRange(filter.getStartTime(), filter.getEndTime());
            }

            // Apply pagination
            if (filter.getPage() != null && filter.getPageSize() != null && filter.getPage() > 0) {
                int start = (filter.getPage() - 1) * filter.getPageSize();
                int end = Math.min(start + filter.getPageSize(), errors.size());
                if (start < errors.size()) {
                    return errors.subList(start, end);
                }
                return List.of();
            }
            return errors;
        } catch (Exception e) {
            log.warn("Failed to get error logs: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 账号可用性信息
     */
    @lombok.Data
    public static class AccountAvailability {
        private List<AccountStatus> accounts;
    }

    @lombok.Data
    public static class AccountStatus {
        private Long accountId;
        private String platform;
        private Boolean isAvailable;
        private Boolean isRateLimited;
        private Boolean hasError;
    }

    /**
     * 获取账号可用性（模拟实现）
     */
    public AccountAvailability getAccountAvailability(String platformFilter, Long groupIdFilter) {
        // 简化实现，实际应查询账号表获取真实状态
        AccountAvailability availability = new AccountAvailability();
        availability.setAccounts(List.of());
        return availability;
    }
}
