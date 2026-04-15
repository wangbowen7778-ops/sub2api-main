package com.sub2api.module.account.service.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 调度 Outbox 事件
 * 用于账号和分组变更的事件驱动同步
 *
 * @author Sub2API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerOutboxEvent {

    /**
     * 事件 ID
     */
    private Long id;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 关联的账号 ID（可选）
     */
    private Long accountId;

    /**
     * 关联的分组 ID（可选）
     */
    private Long groupId;

    /**
     * 事件负载数据
     */
    private Map<String, Object> payload;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 事件类型常量
     */
    public static final String EVENT_ACCOUNT_CHANGED = "account_changed";
    public static final String EVENT_ACCOUNT_GROUPS_CHANGED = "account_groups_changed";
    public static final String EVENT_ACCOUNT_BULK_CHANGED = "account_bulk_changed";
    public static final String EVENT_ACCOUNT_LAST_USED = "account_last_used";
    public static final String EVENT_GROUP_CHANGED = "group_changed";
    public static final String EVENT_FULL_REBUILD = "full_rebuild";
}
