package com.sub2api.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.billing.mapper.UsageCleanupTaskMapper;
import com.sub2api.module.billing.model.entity.UsageCleanupFilters;
import com.sub2api.module.billing.model.entity.UsageCleanupTask;
import com.sub2api.module.common.model.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 使用记录清理仓储实现
 *
 * @author Sub2API
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UsageCleanupRepositoryImpl implements UsageCleanupRepository {

    private final UsageCleanupTaskMapper usageCleanupTaskMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createTask(UsageCleanupTask task) {
        try {
            String filtersJson = objectMapper.writeValueAsString(task.getFilters());
            task.setFilters(filtersJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize filters", e);
            task.setFilters("{}");
        }
        task.setStatus("pending");
        task.setDeletedRows(0L);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        usageCleanupTaskMapper.insert(task);
    }

    @Override
    public java.util.List<UsageCleanupTask> listTasks(int page, int pageSize) {
        IPage<UsageCleanupTask> taskPage = usageCleanupTaskMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<UsageCleanupTask>()
                        .orderByDesc(UsageCleanupTask::getCreatedAt)
        );

        // 反序列化 filters
        for (UsageCleanupTask task : taskPage.getRecords()) {
            parseFilters(task);
        }

        return taskPage.getRecords();
    }

    @Override
    @Transactional
    public UsageCleanupTask claimNextPendingTask(long staleRunningAfterSeconds) {
        // 优先查找 pending 任务
        UsageCleanupTask task = usageCleanupTaskMapper.selectOne(
                new LambdaQueryWrapper<UsageCleanupTask>()
                        .eq(UsageCleanupTask::getStatus, "pending")
                        .orderByAsc(UsageCleanupTask::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (task != null) {
            markTaskRunning(task.getId());
            parseFilters(task);
            return task;
        }

        // 没有 pending 任务，查找过期的 running 任务
        LocalDateTime staleTime = LocalDateTime.now().minusSeconds(staleRunningAfterSeconds);
        task = usageCleanupTaskMapper.selectOne(
                new LambdaQueryWrapper<UsageCleanupTask>()
                        .eq(UsageCleanupTask::getStatus, "running")
                        .lt(UsageCleanupTask::getUpdatedAt, staleTime)
                        .orderByAsc(UsageCleanupTask::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (task != null) {
            markTaskRunning(task.getId());
            parseFilters(task);
        }

        return task;
    }

    private void markTaskRunning(Long taskId) {
        UsageCleanupTask update = new UsageCleanupTask();
        update.setId(taskId);
        update.setStatus("running");
        update.setStartedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        usageCleanupTaskMapper.updateById(update);
    }

    @Override
    public String getTaskStatus(Long taskId) {
        UsageCleanupTask task = usageCleanupTaskMapper.selectById(taskId);
        return task != null ? task.getStatus() : null;
    }

    @Override
    @Transactional
    public void updateTaskProgress(Long taskId, long deletedRows) {
        UsageCleanupTask update = new UsageCleanupTask();
        update.setId(taskId);
        update.setDeletedRows(deletedRows);
        update.setUpdatedAt(LocalDateTime.now());
        usageCleanupTaskMapper.updateById(update);
    }

    @Override
    @Transactional
    public boolean cancelTask(Long taskId, Long canceledBy) {
        UsageCleanupTask update = new UsageCleanupTask();
        update.setId(taskId);
        update.setStatus("canceled");
        update.setCanceledBy(canceledBy);
        update.setCanceledAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        return usageCleanupTaskMapper.updateById(update) > 0;
    }

    @Override
    @Transactional
    public void markTaskSucceeded(Long taskId, long deletedRows) {
        UsageCleanupTask update = new UsageCleanupTask();
        update.setId(taskId);
        update.setStatus("succeeded");
        update.setDeletedRows(deletedRows);
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        usageCleanupTaskMapper.updateById(update);
    }

    @Override
    @Transactional
    public void markTaskFailed(Long taskId, long deletedRows, String errorMessage) {
        UsageCleanupTask update = new UsageCleanupTask();
        update.setId(taskId);
        update.setStatus("failed");
        update.setDeletedRows(deletedRows);
        update.setErrorMessage(errorMessage);
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        usageCleanupTaskMapper.updateById(update);
    }

    @Override
    public long deleteUsageLogsBatch(UsageCleanupFilters filters, int limit) {
        StringBuilder sql = new StringBuilder("DELETE FROM usage_logs WHERE id IN (");
        sql.append("SELECT id FROM usage_logs WHERE created_at >= ? AND created_at <= ?");

        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(filters.getStartTime());
        params.add(filters.getEndTime());

        appendCondition(sql, params, "user_id", filters.getUserId());
        appendCondition(sql, params, "api_key_id", filters.getApiKeyId());
        appendCondition(sql, params, "account_id", filters.getAccountId());
        appendCondition(sql, params, "group_id", filters.getGroupId());

        if (filters.getModel() != null && !filters.getModel().isEmpty()) {
            sql.append(" AND model = ?");
            params.add(filters.getModel());
        }

        if (filters.getRequestType() != null && !filters.getRequestType().isEmpty()) {
            sql.append(" AND request_type = ?");
            params.add(filters.getRequestType());
        }

        if (filters.getStream() != null) {
            sql.append(" AND is_stream = ?");
            params.add(filters.getStream());
        }

        if (filters.getBillingType() != null) {
            sql.append(" AND billing_type = ?");
            params.add(filters.getBillingType());
        }

        sql.append(" LIMIT ?");
        params.add(limit);

        sql.append(")");

        try {
            int deleted = jdbcTemplate.update(sql.toString(), params.toArray());
            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete usage logs batch", e);
            return 0;
        }
    }

    private void appendCondition(StringBuilder sql, java.util.List<Object> params, String field, Object value) {
        if (value != null) {
            if (value instanceof Long && (Long) value <= 0) {
                return;
            }
            sql.append(" AND ").append(field).append(" = ?");
            params.add(value);
        }
    }

    private void parseFilters(UsageCleanupTask task) {
        if (task.getFilters() == null || task.getFilters().isEmpty()) {
            return;
        }
        try {
            UsageCleanupFilters filters = objectMapper.readValue(task.getFilters(), UsageCleanupFilters.class);
            task.setFilters(objectMapper.writeValueAsString(filters));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse filters for task {}", task.getId(), e);
        }
    }

    /**
     * 获取反序列化后的 Filters 对象
     */
    public static UsageCleanupFilters parseFiltersJson(String filtersJson) {
        if (filtersJson == null || filtersJson.isEmpty()) {
            return new UsageCleanupFilters();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(filtersJson, UsageCleanupFilters.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse filters JSON", e);
            return new UsageCleanupFilters();
        }
    }
}
