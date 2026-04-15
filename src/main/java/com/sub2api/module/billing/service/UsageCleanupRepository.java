package com.sub2api.module.billing.service;

import com.sub2api.module.billing.model.entity.UsageCleanupFilters;
import com.sub2api.module.billing.model.entity.UsageCleanupTask;
import com.sub2api.module.common.model.vo.PageResult;

import java.util.List;

/**
 * 使用记录清理仓储接口
 *
 * @author Sub2API
 */
public interface UsageCleanupRepository {

    /**
     * 创建清理任务
     */
    void createTask(UsageCleanupTask task);

    /**
     * 查询任务列表
     */
    List<UsageCleanupTask> listTasks(int page, int pageSize);

    /**
     * 抢占下一条可执行任务
     *
     * @param staleRunningAfterSeconds running 状态超过此秒数视为过期，可重新抢占
     * @return 任务，不存在返回 null
     */
    UsageCleanupTask claimNextPendingTask(long staleRunningAfterSeconds);

    /**
     * 获取任务状态
     *
     * @param taskId 任务ID
     * @return 状态
     */
    String getTaskStatus(Long taskId);

    /**
     * 更新任务进度
     */
    void updateTaskProgress(Long taskId, long deletedRows);

    /**
     * 取消任务
     *
     * @param taskId     任务ID
     * @param canceledBy 取消人
     * @return 是否成功
     */
    boolean cancelTask(Long taskId, Long canceledBy);

    /**
     * 标记任务成功
     */
    void markTaskSucceeded(Long taskId, long deletedRows);

    /**
     * 标记任务失败
     */
    void markTaskFailed(Long taskId, long deletedRows, String errorMessage);

    /**
     * 批量删除使用记录
     *
     * @param filters 过滤条件
     * @param limit   每批删除数量
     * @return 实际删除数量
     */
    long deleteUsageLogsBatch(UsageCleanupFilters filters, int limit);
}
