package com.sub2api.module.ops.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.common.service.EmailService;
import com.sub2api.module.ops.model.vo.OpsDashboardOverview;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ops Scheduled Report Service
 * 定时报表服务，生成并发送日报、周报、错误摘要、账号健康报告
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsScheduledReportService {

    private final OpsService opsService;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ops.enabled:true}")
    private boolean opsEnabled;

    @Value("${ops.scheduled-reports.enabled:true}")
    private boolean reportsEnabled;

    @Value("${ops.scheduled-reports.leader-lock-ttl-seconds:300}")
    private int leaderLockTtlSeconds;

    @Value("${ops.timezone:Asia/Shanghai}")
    private String timezone;

    // Leader lock key
    private static final String LEADER_LOCK_KEY = "ops:scheduled_reports:leader";
    private static final String LAST_RUN_KEY_PREFIX = "ops:scheduled_reports:last_run:";

    // Instance ID
    private final String instanceId = UUID.randomUUID().toString();

    // Running state
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Report configuration
    @Value("${ops.scheduled-reports.daily-summary-enabled:true}")
    private boolean dailySummaryEnabled;

    @Value("${ops.scheduled-reports.daily-summary-cron:0 0 8 * * ?}")
    private String dailySummaryCron;

    @Value("${ops.scheduled-reports.weekly-summary-enabled:true}")
    private boolean weeklySummaryEnabled;

    @Value("${ops.scheduled-reports.weekly-summary-cron:0 0 8 ? * MON}")
    private String weeklySummaryCron;

    @Value("${ops.scheduled-reports.error-digest-enabled:true}")
    private boolean errorDigestEnabled;

    @Value("${ops.scheduled-reports.error-digest-cron:0 0 9 * * ?}")
    private String errorDigestCron;

    @Value("${ops.scheduled-reports.account-health-enabled:true}")
    private boolean accountHealthEnabled;

    @Value("${ops.scheduled-reports.account-health-cron:0 0 10 * * ?}")
    private String accountHealthCron;

    @Value("${ops.scheduled-reports.recipients:}")
    private List<String> reportRecipients;

    private ZoneId zoneId;
    private CronParser cronParser;

    @PostConstruct
    public void init() {
        // Initialize cron parser with UNIX definition
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        this.cronParser = new CronParser(cronDefinition);

        try {
            this.zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            this.zoneId = ZoneId.systemDefault();
            log.warn("Failed to parse timezone '{}', using system default: {}", timezone, e.getMessage());
        }

        if (reportsEnabled && opsEnabled) {
            log.info("OpsScheduledReportService initialized with instanceId={}, timezone={}", instanceId, zoneId);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        log.info("OpsScheduledReportService stopped");
    }

    /**
     * 主调度循环 - 每分钟检查一次
     */
    @Scheduled(fixedRate = 60000)
    public void runOnce() {
        if (!reportsEnabled || !opsEnabled) {
            return;
        }

        if (!opsService.isMonitoringEnabled()) {
            log.debug("Ops monitoring is disabled, skipping scheduled reports");
            return;
        }

        // 尝试获取 leader 锁
        if (!tryAcquireLeaderLock()) {
            return;
        }

        try {
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            // 检查每个报表类型
            checkAndRunReport("daily_summary", dailySummaryEnabled, dailySummaryCron, Duration.ofHours(24), now);
            checkAndRunReport("weekly_summary", weeklySummaryEnabled, weeklySummaryCron, Duration.ofDays(7), now);
            checkAndRunReport("error_digest", errorDigestEnabled, errorDigestCron, Duration.ofHours(24), now);
            checkAndRunReport("account_health", accountHealthEnabled, accountHealthCron, Duration.ofHours(24), now);

        } catch (Exception e) {
            log.error("Error during scheduled report evaluation: {}", e.getMessage());
        }
    }

    /**
     * 检查并运行报表
     */
    private void checkAndRunReport(String reportType, boolean enabled, String cronExpr, Duration timeRange, ZonedDateTime now) {
        if (!enabled || cronExpr == null || cronExpr.isBlank()) {
            return;
        }

        try {
            Cron cron = cronParser.parse(cronExpr);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            ZonedDateTime lastRun = getLastRunAt(reportType);
            ZonedDateTime baseTime = lastRun != null ? lastRun : now.minus(1, ChronoUnit.MINUTES);
            Optional<ZonedDateTime> nextRun = executionTime.nextExecution(baseTime);

            if (nextRun.isPresent() && nextRun.get().isBefore(now.plus(1, ChronoUnit.MINUTES))) {
                log.info("Running scheduled report: type={}, lastRun={}, nextRun={}", reportType, lastRun, nextRun.get());
                runReport(reportType, timeRange, now);
                setLastRunAt(reportType, now);
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate report '{}': {}", reportType, e.getMessage());
        }
    }

    /**
     * 运行报表
     */
    private int runReport(String reportType, Duration timeRange, ZonedDateTime now) {
        ZonedDateTime start = now.minus(timeRange);
        ZonedDateTime end = now;

        String subject = String.format("[Ops Report] %s", getReportName(reportType));
        String content;

        try {
            content = generateReportHTML(reportType, start, end);
        } catch (Exception e) {
            log.error("Failed to generate report '{}': {}", reportType, e.getMessage());
            recordHeartbeatError(reportType, e.getMessage());
            return 0;
        }

        if (content == null || content.isBlank()) {
            log.debug("Report '{}' generated empty content, skipping send", reportType);
            return 0;
        }

        List<String> recipients = getRecipients();
        if (recipients.isEmpty()) {
            log.warn("No report recipients configured for '{}'", reportType);
            return 0;
        }

        int sentCount = 0;
        for (String recipient : recipients) {
            try {
                emailService.sendEmail(subject, content);
                sentCount++;
            } catch (Exception e) {
                log.warn("Failed to send report to '{}': {}", recipient, e.getMessage());
            }
        }

        recordHeartbeatSuccess(reportType, sentCount);
        return sentCount;
    }

    /**
     * 生成报表 HTML 内容
     */
    private String generateReportHTML(String reportType, ZonedDateTime start, ZonedDateTime end) {
        return switch (reportType) {
            case "daily_summary", "weekly_summary" -> generateSummaryReport(reportType, start, end);
            case "error_digest" -> generateErrorDigestReport(start, end);
            case "account_health" -> generateAccountHealthReport(start, end);
            default -> {
                log.warn("Unknown report type: {}", reportType);
                yield "";
            }
        };
    }

    /**
     * 生成汇总报表
     */
    private String generateSummaryReport(String reportType, ZonedDateTime start, ZonedDateTime end) {
        OpsService.OpsDashboardFilter filter = new OpsService.OpsDashboardFilter();
        filter.setStartTime(start.toLocalDateTime());
        filter.setEndTime(end.toLocalDateTime());
        filter.setQueryMode("auto");

        OpsDashboardOverview overview = opsService.getDashboardOverview(filter);
        if (overview == null) {
            return "<h2>" + escapeHtml(reportType) + "</h2><p>No data available.</p>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escapeHtml(getReportName(reportType))).append("</h2>");
        sb.append("<p><b>Period</b>: ").append(start.format(DateTimeFormatter.ISO_INSTANT))
           .append(" ~ ").append(end.format(DateTimeFormatter.ISO_INSTANT)).append(" (UTC)</p>");
        sb.append("<ul>");

        if (overview.getRequestStats() != null) {
            sb.append("<li><b>Total Requests</b>: ").append(overview.getRequestStats().getTotalRequests()).append("</li>");
            sb.append("<li><b>Success</b>: ").append(overview.getRequestStats().getSuccessfulRequests()).append("</li>");
        }

        if (overview.getErrorStats() != null) {
            sb.append("<li><b>Errors (SLA)</b>: ").append(overview.getErrorStats().getTotalErrors()).append("</li>");
            double errorRate = 0.0;
            if (overview.getRequestStats() != null && overview.getRequestStats().getTotalRequests() > 0) {
                errorRate = (double) overview.getErrorStats().getTotalErrors() / overview.getRequestStats().getTotalRequests();
            }
            sb.append("<li><b>Error Rate</b>: ").append(String.format("%.2f%%", errorRate * 100)).append("</li>");
        }

        if (overview.getSystemMetrics() != null && overview.getSystemMetrics().getCpuUsage() != null) {
            sb.append("<li><b>CPU Usage</b>: ").append(String.format("%.1f%%", overview.getSystemMetrics().getCpuUsage())).append("</li>");
            sb.append("<li><b>Memory Usage</b>: ").append(String.format("%.1f%%", overview.getSystemMetrics().getMemoryUsage())).append("</li>");
        }

        sb.append("</ul>");
        return sb.toString();
    }

    /**
     * 生成错误摘要报表
     */
    private String generateErrorDigestReport(ZonedDateTime start, ZonedDateTime end) {
        OpsService.OpsErrorLogFilter filter = new OpsService.OpsErrorLogFilter();
        filter.setStartTime(start.toLocalDateTime());
        filter.setEndTime(end.toLocalDateTime());
        filter.setPage(1);
        filter.setPageSize(10);

        List<com.sub2api.module.ops.model.entity.OpsErrorLog> errors = opsService.getErrorLogs(filter);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escapeHtml(getReportName("error_digest"))).append("</h2>");
        sb.append("<p><b>Period</b>: ").append(start.format(DateTimeFormatter.ISO_INSTANT))
           .append(" ~ ").append(end.format(DateTimeFormatter.ISO_INSTANT)).append(" (UTC)</p>");

        if (errors == null || errors.isEmpty()) {
            sb.append("<p>No recent errors.</p>");
            return sb.toString();
        }

        sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse;'>");
        sb.append("<thead><tr><th>Time</th><th>Platform</th><th>Status</th><th>Message</th></tr></thead>");
        sb.append("<tbody>");

        for (var error : errors) {
            sb.append("<tr>");
            sb.append("<td>").append(error.getCreatedAt() != null ? error.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "-").append("</td>");
            sb.append("<td>").append(escapeHtml(nullSafe(error.getPlatform()))).append("</td>");
            sb.append("<td>").append(error.getStatusCode() != null ? error.getStatusCode() : "-").append("</td>");
            sb.append("<td>").append(escapeHtml(truncateString(nullSafe(error.getErrorMessage()), 180))).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * 生成账号健康报表
     */
    private String generateAccountHealthReport(ZonedDateTime start, ZonedDateTime end) {
        var availability = opsService.getAccountAvailability(null, null);

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escapeHtml(getReportName("account_health"))).append("</h2>");
        sb.append("<p><b>Period</b>: ").append(start.format(DateTimeFormatter.ISO_INSTANT))
           .append(" ~ ").append(end.format(DateTimeFormatter.ISO_INSTANT)).append(" (UTC)</p>");

        if (availability == null || availability.getAccounts() == null) {
            sb.append("<p>No account data available.</p>");
            return sb.toString();
        }

        int total = 0, available = 0, rateLimited = 0, hasError = 0;

        for (var account : availability.getAccounts()) {
            if (account == null) continue;
            total++;
            if (Boolean.TRUE.equals(account.getIsAvailable())) available++;
            if (Boolean.TRUE.equals(account.getIsRateLimited())) rateLimited++;
            if (Boolean.TRUE.equals(account.getHasError())) hasError++;
        }

        sb.append("<ul>");
        sb.append("<li><b>Total Accounts</b>: ").append(total).append("</li>");
        sb.append("<li><b>Available</b>: ").append(available).append("</li>");
        sb.append("<li><b>Rate Limited</b>: ").append(rateLimited).append("</li>");
        sb.append("<li><b>Error</b>: ").append(hasError).append("</li>");
        sb.append("</ul>");
        sb.append("<p>Note: This report reflects account availability status only.</p>");

        return sb.toString();
    }

    /**
     * 获取报表名称
     */
    private String getReportName(String reportType) {
        return switch (reportType) {
            case "daily_summary" -> "日报";
            case "weekly_summary" -> "周报";
            case "error_digest" -> "错误摘要";
            case "account_health" -> "账号健康";
            default -> reportType;
        };
    }

    /**
     * 获取收件人列表
     */
    private List<String> getRecipients() {
        if (reportRecipients == null || reportRecipients.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> recipients = new CopyOnWriteArrayList<>();
        for (String r : reportRecipients) {
            String trimmed = r != null ? r.trim().toLowerCase() : "";
            if (!trimmed.isEmpty() && trimmed.contains("@")) {
                recipients.add(trimmed);
            }
        }
        return recipients;
    }

    /**
     * 尝试获取 Leader 锁
     */
    private boolean tryAcquireLeaderLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LEADER_LOCK_KEY, instanceId, Duration.ofSeconds(leaderLockTtlSeconds));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Failed to acquire leader lock: {}", e.getMessage());
            return true; // Fail open - run without lock if Redis is unavailable
        }
    }

    /**
     * 获取上次运行时间
     */
    private ZonedDateTime getLastRunAt(String reportType) {
        try {
            String key = LAST_RUN_KEY_PREFIX + reportType;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && !value.isBlank()) {
                long epochSecond = Long.parseLong(value.trim());
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), zoneId);
            }
        } catch (Exception e) {
            log.warn("Failed to get last run time for '{}': {}", reportType, e.getMessage());
        }
        return null;
    }

    /**
     * 设置上次运行时间
     */
    private void setLastRunAt(String reportType, ZonedDateTime time) {
        try {
            String key = LAST_RUN_KEY_PREFIX + reportType;
            long epochSecond = time.toEpochSecond();
            redisTemplate.opsForValue().set(key, String.valueOf(epochSecond), Duration.ofDays(14));
        } catch (Exception e) {
            log.warn("Failed to set last run time for '{}': {}", reportType, e.getMessage());
        }
    }

    /**
     * 记录心跳成功
     */
    private void recordHeartbeatSuccess(String reportType, int sentCount) {
        try {
            Map<String, Object> heartbeat = new ConcurrentHashMap<>();
            heartbeat.put("lastRunAt", Instant.now().toString());
            heartbeat.put("lastSuccessAt", Instant.now().toString());
            heartbeat.put("sentCount", sentCount);
            heartbeat.put("instanceId", instanceId);

            String key = "ops:scheduled_reports:heartbeat:" + reportType;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(heartbeat), Duration.ofHours(25));
        } catch (Exception e) {
            log.warn("Failed to record heartbeat success for '{}': {}", reportType, e.getMessage());
        }
    }

    /**
     * 记录心跳错误
     */
    private void recordHeartbeatError(String reportType, String errorMsg) {
        try {
            Map<String, Object> heartbeat = new ConcurrentHashMap<>();
            heartbeat.put("lastRunAt", Instant.now().toString());
            heartbeat.put("lastErrorAt", Instant.now().toString());
            heartbeat.put("lastError", truncateString(errorMsg, 500));
            heartbeat.put("instanceId", instanceId);

            String key = "ops:scheduled_reports:heartbeat:" + reportType;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(heartbeat), Duration.ofHours(25));
        } catch (Exception e) {
            log.warn("Failed to record heartbeat error for '{}': {}", reportType, e.getMessage());
        }
    }

    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Null 安全字符串
     */
    private String nullSafe(String text) {
        return text != null ? text : "";
    }

    /**
     * 截断字符串
     */
    private String truncateString(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 获取服务状态
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "instance_id", instanceId,
                "running", running.get(),
                "timezone", zoneId.toString(),
                "enabled", reportsEnabled && opsEnabled
        );
    }
}
