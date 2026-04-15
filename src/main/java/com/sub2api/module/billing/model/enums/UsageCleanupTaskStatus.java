package com.sub2api.module.billing.model.enums;

/**
 * 清理任务状态枚举
 *
 * @author Sub2API
 */
public final class UsageCleanupTaskStatus {

    private UsageCleanupTaskStatus() {
    }

    /**
     * 等待执行
     */
    public static final String PENDING = "pending";

    /**
     * 执行中
     */
    public static final String RUNNING = "running";

    /**
     * 已完成
     */
    public static final String SUCCEEDED = "succeeded";

    /**
     * 失败
     */
    public static final String FAILED = "failed";

    /**
     * 已取消
     */
    public static final String CANCELED = "canceled";
}
