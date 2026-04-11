package com.sub2api.module.ops.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ops 仪表板概览
 */
@Data
@Accessors(chain = true)
public class OpsDashboardOverview implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时间范围
     */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * 请求统计
     */
    private RequestStats requestStats;

    /**
     * 错误统计
     */
    private ErrorStats errorStats;

    /**
     * 账号可用性
     */
    private List<AccountAvailability> accountAvailabilities;

    /**
     * 系统指标
     */
    private SystemMetrics systemMetrics;

    /**
     * 任务心跳
     */
    private List<JobHeartbeat> jobHeartbeats;

    /**
     * 健康评分 (0-100)
     */
    private Integer healthScore;

    /**
     * 查询模式: raw, preagg, auto
     */
    private String queryMode;

    /**
     * 请求统计
     */
    @Data
    public static class RequestStats implements Serializable {
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private double successRate;
        private double avgLatencyMs;
        private double p99LatencyMs;
    }

    /**
     * 错误统计
     */
    @Data
    public static class ErrorStats implements Serializable {
        private long totalErrors;
        private long rateLimitErrors;
        private long authErrors;
        private long upstreamErrors;
        private long timeoutErrors;
        private long otherErrors;
        private Map<String, Long> errorsByCode;
        private Map<String, Long> errorsByPhase;
    }

    /**
     * 账号可用性
     */
    @Data
    public static class AccountAvailability implements Serializable {
        private Long accountId;
        private String accountName;
        private String platform;
        private String status;
        private double availabilityPercent;
        private long totalRequests;
        private long errorCount;
    }

    /**
     * 系统指标
     */
    @Data
    public static class SystemMetrics implements Serializable {
        private Integer dbMaxOpenConns;
        private Integer dbOpenConns;
        private Integer redisPoolSize;
        private Integer redisActiveConns;
        private Double cpuUsage;
        private Double memoryUsage;
        private LocalDateTime collectedAt;
    }

    /**
     * 任务心跳
     */
    @Data
    public static class JobHeartbeat implements Serializable {
        private String jobName;
        private String status;
        private LocalDateTime lastHeartbeat;
        private String message;
    }
}
