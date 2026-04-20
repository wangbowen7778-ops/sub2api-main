package com.sub2api.module.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.billing.mapper.UsageCleanupTaskMapper;
import com.sub2api.module.billing.model.entity.UsageCleanupFilters;
import com.sub2api.module.billing.model.entity.UsageCleanupTask;
import com.sub2api.module.billing.model.enums.UsageCleanupTaskStatus;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.dashboard.service.DashboardAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用记录清理服务
 * 负责创建与执行使用记录清理任务
 *
 * @author Sub2API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageCleanupService {

    private final UsageCleanupRepository usageCleanupRepository;
    private final UsageCleanupTaskMapper usageCleanupTaskMapper;
    private final DashboardAggregationService dashboardAggregationService;
    private final ObjectMapper objectMapper;

    // 运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 配置项
    @Value("${sub2api.usage-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${sub2api.usage-cleanup.worker-interval-seconds:10}")
    private int workerIntervalSeconds;

    @Value("${sub2api.usage-cleanup.batch-size:5000}")
    private int batchSize;

    @Value("${sub2api.usage-cleanup.max-range-days:31}")
    private int maxRangeDays;

    @Value("${sub2api.usage-cleanup.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Value("${sub2api.usage-cleanup.stale-running-after-seconds:600}")
    private int staleRunningAfterSeconds;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("[UsageCleanup] not started (disabled)");
            return;
        }
        log.info("[UsageCleanup] started (interval={}s max_range_days={} batch_size={} task_timeout={}m)",
                workerIntervalSeconds, maxRangeDays, batchSize, taskTimeoutMinutes);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        log.info("[UsageCleanup] stopped");
    }

    /**
     * 创建清理任务
     */
    @Transactional
    public UsageCleanupTask createTask(UsageCleanupFilters filters, Long createdBy) {
        if (createdBy == null || createdBy <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid creator");
        }

        validateFilters(filters);

        UsageCleanupTask task = new UsageCleanupTask();
        task.setFilters(serializeFilters(filters));
        task.setCreatedBy(createdBy);

        try {
            usageCleanupRepository.createTask(task);
            log.info("[UsageCleanup] create_task persisted: task={} operator={} deleted_rows={}",
                    task.getId(), createdBy, task.getDeletedRows());
            return task;
        } catch (Exception e) {
            log.error("[UsageCleanup] create_task persist failed: operator={} err={}", createdBy, e.getMessage());
            throw new BusinessException(ErrorCode.FAIL, "Failed to create cleanup task");
        }
    }

    /**
     * 查询任务列表
     */
    public java.util.List<UsageCleanupTask> listTasks(int page, int pageSize) {
        return usageCleanupRepository.listTasks(page, pageSize);
    }

    /**
     * 取消任务
     */
    @Transactional
    public void cancelTask(Long taskId, Long canceledBy) {
        if (taskId == null || canceledBy == null || canceledBy <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid parameters");
        }

        String status = usageCleanupRepository.getTaskStatus(taskId);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Task not found");
        }

        if (UsageCleanupTaskStatus.CANCELED.equals(status)) {
            log.info("[UsageCleanup] cancel_task idempotent hit: task={} operator={}", taskId, canceledBy);
            return;
        }

        if (!UsageCleanupTaskStatus.PENDING.equals(status) && !UsageCleanupTaskStatus.RUNNING.equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Task cannot be canceled in current status");
        }

        boolean success = usageCleanupRepository.cancelTask(taskId, canceledBy);
        if (!success) {
            // 可能存在并发情况，重新检查状态
            status = usageCleanupRepository.getTaskStatus(taskId);
            if (UsageCleanupTaskStatus.CANCELED.equals(status)) {
                log.info("[UsageCleanup] cancel_task idempotent race hit: task={} operator={}", taskId, canceledBy);
                return;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Failed to cancel task");
        }

        log.info("[UsageCleanup] cancel_task done: task={} operator={}", taskId, canceledBy);
    }

    /**
     * 定期执行清理任务（每10秒）
     */
    @Scheduled(fixedDelayString = "${sub2api.usage-cleanup.worker-interval-seconds:10}000")
    public void runOnce() {
        if (!enabled) {
            return;
        }

        // 确保只有一个 worker 在运行
        if (!running.compareAndSet(false, true)) {
            log.debug("[UsageCleanup] run_once skipped: already_running=true");
            return;
        }

        try {
            executeTask();
        } finally {
            running.set(false);
        }
    }

    /**
     * 执行单个任务
     */
    private void executeTask() {
        UsageCleanupTask task = usageCleanupRepository.claimNextPendingTask(staleRunningAfterSeconds);
        if (task == null) {
            log.debug("[UsageCleanup] run_once done: no_task=true");
            return;
        }

        log.info("[UsageCleanup] task claimed: task={} status={} created_by={} deleted_rows={}",
                task.getId(), task.getStatus(), task.getCreatedBy(), task.getDeletedRows());

        UsageCleanupFilters filters = deserializeFilters(task.getFilters());
        int deletedTotal = task.getDeletedRows() != null ? task.getDeletedRows().intValue() : 0;
        OffsetDateTime start = OffsetDateTime.now();
        int batchNum = 0;

        try {
            while (true) {
                // 检查是否超时
                if (Duration.between(start, OffsetDateTime.now()).toMinutes() >= taskTimeoutMinutes) {
                    log.info("[UsageCleanup] task timeout: task={}", task.getId());
                    break;
                }

                // 检查是否被取消
                String status = usageCleanupRepository.getTaskStatus(task.getId());
                if (UsageCleanupTaskStatus.CANCELED.equals(status)) {
                    log.info("[UsageCleanup] task canceled: task={} deleted_rows={} duration={}",
                            task.getId(), deletedTotal, Duration.between(start, OffsetDateTime.now()));
                    return;
                }

                // 执行批量删除
                long deleted = usageCleanupRepository.deleteUsageLogsBatch(filters, batchSize);
                deletedTotal += deleted;

                if (deleted > 0) {
                    // 更新进度
                    usageCleanupRepository.updateTaskProgress(task.getId(), deletedTotal);
                }

                batchNum++;
                if (batchNum <= 3 || batchNum % 20 == 0 || deleted < batchSize) {
                    log.info("[UsageCleanup] task batch done: task={} batch={} deleted={} deleted_total={}",
                            task.getId(), batchNum, deleted, deletedTotal);
                }

                // 没有更多数据可删除
                if (deleted == 0 || deleted < batchSize) {
                    break;
                }
            }

            // 标记任务成功
            usageCleanupRepository.markTaskSucceeded(task.getId(), deletedTotal);
            log.info("[UsageCleanup] task succeeded: task={} deleted_rows={} duration={}",
                    task.getId(), deletedTotal, Duration.between(start, OffsetDateTime.now()));

            // 触发 Dashboard 重新聚合
            triggerDashboardRecompute(filters);

        } catch (Exception e) {
            log.error("[UsageCleanup] task failed: task={} deleted_rows={} err={}",
                    task.getId(), deletedTotal, e.getMessage());
            usageCleanupRepository.markTaskFailed(task.getId(), deletedTotal, e.getMessage());
        }
    }

    /**
     * 触发 Dashboard 重新聚合
     */
    private void triggerDashboardRecompute(UsageCleanupFilters filters) {
        if (dashboardAggregationService == null) {
            return;
        }

        try {
            dashboardAggregationService.recomputeRange(filters.getStartTime(), filters.getEndTime());
            log.info("[UsageCleanup] trigger dashboard recompute: start={} end={}",
                    filters.getStartTime(), filters.getEndTime());
        } catch (Exception e) {
            log.warn("[UsageCleanup] trigger dashboard recompute failed: err={}", e.getMessage());
        }
    }

    /**
     * 验证过滤条件
     */
    private void validateFilters(UsageCleanupFilters filters) {
        if (filters == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Filters cannot be null");
        }

        if (filters.getStartTime() == null || filters.getEndTime() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "start_time and end_time are required");
        }

        if (filters.getEndTime().isBefore(filters.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "end_time must be after start_time");
        }

        long days = Duration.between(filters.getStartTime(), filters.getEndTime()).toDays();
        if (maxRangeDays > 0 && days > maxRangeDays) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("date range exceeds %d days", maxRangeDays));
        }
    }

    /**
     * 序列化过滤器为 JSON
     */
    private String serializeFilters(UsageCleanupFilters filters) {
        if (filters == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (Exception e) {
            log.error("Failed to serialize filters", e);
            return "{}";
        }
    }

    /**
     * 反序列化过滤器
     */
    private UsageCleanupFilters deserializeFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isEmpty()) {
            return new UsageCleanupFilters();
        }
        try {
            return objectMapper.readValue(filtersJson, UsageCleanupFilters.class);
        } catch (Exception e) {
            log.error("Failed to deserialize filters", e);
            return new UsageCleanupFilters();
        }
    }
}
