package com.sub2api.module.ops.service;

import com.sub2api.module.common.service.EmailService;
import com.sub2api.module.ops.model.vo.OpsDashboardOverview;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ops Alert Evaluator Service
 * 告警评估和通知服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAlertEvaluatorService {

    private final OpsService opsService;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    // 配置常量
    private static final String LEADER_LOCK_KEY = "ops:alert:evaluator:leader";
    private static final Duration LEADER_LOCK_TTL = Duration.ofSeconds(90);
    private static final Duration EVALUATION_INTERVAL = Duration.ofSeconds(60);
    private static final Duration EMAIL_RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int EMAIL_RATE_LIMIT_MAX = 10;

    // 实例 ID
    private final String instanceId = UUID.randomUUID().toString();

    // 运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread evaluationThread;
    private final ConcurrentHashMap<Long, AlertRuleState> ruleStates = new ConcurrentHashMap<>();

    // 邮件限流器
    private final SlidingWindowLimiter emailLimiter = new SlidingWindowLimiter(EMAIL_RATE_LIMIT_MAX, EMAIL_RATE_LIMIT_WINDOW);

    @PostConstruct
    public void init() {
        start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (evaluationThread != null) {
            evaluationThread.interrupt();
        }
        log.info("OpsAlertEvaluatorService stopped");
    }

    /**
     * 启动评估服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            evaluationThread = new Thread(this::runEvaluationLoop, "ops-alert-evaluator");
            evaluationThread.setDaemon(true);
            evaluationThread.start();
            log.info("OpsAlertEvaluatorService started with instanceId={}", instanceId);
        }
    }

    /**
     * 停止评估服务
     */
    public void stopEvaluation() {
        running.set(false);
    }

    /**
     * 主评估循环
     */
    private void runEvaluationLoop() {
        while (running.get()) {
            try {
                // 尝试获取 leader 锁
                if (tryAcquireLeaderLock()) {
                    evaluateAlerts();
                }
                Thread.sleep(EVALUATION_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Alert evaluation error: {}", e.getMessage());
                try {
                    Thread.sleep(EVALUATION_INTERVAL.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 尝试获取 Leader 锁
     */
    private boolean tryAcquireLeaderLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    LEADER_LOCK_KEY, instanceId, LEADER_LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Failed to acquire leader lock: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 评估告警
     */
    public void evaluateAlerts() {
        if (!opsService.isMonitoringEnabled()) {
            log.debug("Ops monitoring is disabled, skipping alert evaluation");
            return;
        }

        try {
            // 获取仪表板概览
            var filter = new OpsService.OpsDashboardFilter();
            filter.setStartTime(OffsetDateTime.now().minusMinutes(5));
            filter.setEndTime(OffsetDateTime.now());

            var overview = opsService.getDashboardOverview(filter);
            if (overview == null) {
                return;
            }

            // 评估各项指标
            evaluateErrorRateAlert(overview);
            evaluateHealthScoreAlert(overview);
            evaluateSystemMetricsAlert(overview);

        } catch (Exception e) {
            log.error("Error during alert evaluation: {}", e.getMessage());
        }
    }

    /**
     * 评估错误率告警
     */
    private void evaluateErrorRateAlert(OpsDashboardOverview overview) {
        if (overview.getErrorStats() == null) {
            return;
        }

        long totalErrors = overview.getErrorStats().getTotalErrors();
        long totalRequests = overview.getRequestStats() != null ? overview.getRequestStats().getTotalRequests() : 0;

        if (totalRequests == 0) {
            return;
        }

        double errorRate = (double) totalErrors / totalRequests;

        // 错误率 > 5% 触发告警
        if (errorRate > 0.05) {
            String message = String.format("High error rate detected: %.2f%% (%d/%d errors)",
                    errorRate * 100, totalErrors, totalRequests);
            triggerAlert("error_rate_high", message, getAlertSeverity(errorRate, 0.05, 0.1, 0.2));
        }
    }

    /**
     * 评估健康分数告警
     */
    private void evaluateHealthScoreAlert(OpsDashboardOverview overview) {
        int healthScore = overview.getHealthScore();

        // 健康分数 < 50 触发告警
        if (healthScore < 50) {
            String message = String.format("Low health score: %d", healthScore);
            triggerAlert("health_score_low", message, healthScore < 30 ? "critical" : "warning");
        }
    }

    /**
     * 评估系统指标告警
     */
    private void evaluateSystemMetricsAlert(OpsDashboardOverview overview) {
        if (overview.getSystemMetrics() == null) {
            return;
        }

        var metrics = overview.getSystemMetrics();

        // CPU 使用率 > 90% 触发告警
        if (metrics.getCpuUsage() != null && metrics.getCpuUsage() > 90) {
            String message = String.format("High CPU usage: %.1f%%", metrics.getCpuUsage());
            triggerAlert("cpu_high", message, "warning");
        }

        // 内存使用率 > 90% 触发告警
        if (metrics.getMemoryUsage() != null && metrics.getMemoryUsage() > 90) {
            String message = String.format("High memory usage: %.1f%%", metrics.getMemoryUsage());
            triggerAlert("memory_high", message, "warning");
        }
    }

    /**
     * 获取告警级别
     */
    private String getAlertSeverity(double value, double warningThreshold, double errorThreshold, double criticalThreshold) {
        if (value >= criticalThreshold) {
            return "critical";
        } else if (value >= errorThreshold) {
            return "error";
        } else if (value >= warningThreshold) {
            return "warning";
        }
        return "info";
    }

    /**
     * 触发告警
     */
    private void triggerAlert(String alertType, String message, String severity) {
        log.warn("Alert triggered: type={}, severity={}, message={}", alertType, severity, message);

        // 发送邮件通知（如果有配置）
        if (shouldSendEmail(alertType)) {
            sendAlertEmail(alertType, message, severity);
        }
    }

    /**
     * 检查是否应该发送邮件
     */
    private boolean shouldSendEmail(String alertType) {
        return emailLimiter.tryAcquire(alertType);
    }

    /**
     * 发送告警邮件
     */
    private void sendAlertEmail(String alertType, String message, String severity) {
        try {
            String subject = String.format("[%s] %s - Ops Alert", severity.toUpperCase(), alertType);
            emailService.sendEmail(subject, message);
            log.info("Alert email sent: type={}, severity={}", alertType, severity);
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage());
        }
    }

    /**
     * 手动触发告警评估
     */
    public void triggerEvaluation() {
        evaluateAlerts();
    }

    /**
     * 获取服务状态
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "instance_id", instanceId,
                "running", running.get(),
                "rule_states_count", ruleStates.size()
        );
    }

    // ========== 内部类 ==========

    /**
     * 告警规则状态
     */
    @Data
    private static class AlertRuleState {
        private OffsetDateTime lastEvaluatedAt;
        private int consecutiveBreaches;
    }

    /**
     * 滑动窗口限流器
     */
    private static class SlidingWindowLimiter {
        private final int maxCount;
        private final Duration window;
        private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> timestamps = new ConcurrentHashMap<>();

        SlidingWindowLimiter(int maxCount, Duration window) {
            this.maxCount = maxCount;
            this.window = window;
        }

        boolean tryAcquire(String key) {
            long now = System.currentTimeMillis();
            long windowStart = now - window.toMillis();

            ConcurrentLinkedDeque<Long> deque = timestamps.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

            // 清理过期的 timestamp
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }

            // 检查是否超过限制
            if (deque.size() < maxCount) {
                deque.addLast(now);
                return true;
            }

            return false;
        }
    }
}
