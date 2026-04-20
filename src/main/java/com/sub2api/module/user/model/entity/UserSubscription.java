package com.sub2api.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 用户订阅实体
 * 表名: user_subscriptions
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("user_subscriptions")
public class UserSubscription implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订阅ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联的分组ID
     */
    private Long groupId;

    /**
     * 状态: active, expired, suspended, cancelled
     */
    private String status;

    /**
     * 到期时间
     */
    private OffsetDateTime expiresAt;

    /**
     * 开始时间
     */
    private OffsetDateTime startsAt;

    /**
     * 每天窗口开始时间 (UTC)
     */
    private OffsetDateTime dailyWindowStart;

    /**
     * 每周窗口开始时间 (UTC)
     */
    private OffsetDateTime weeklyWindowStart;

    /**
     * 每月窗口开始时间 (UTC)
     */
    private OffsetDateTime monthlyWindowStart;

    /**
     * 当日已使用额度 (USD)
     */
    private BigDecimal dailyUsageUsd;

    /**
     * 当周已使用额度 (USD)
     */
    private BigDecimal weeklyUsageUsd;

    /**
     * 当月已使用额度 (USD)
     */
    private BigDecimal monthlyUsageUsd;

    /**
     * 订阅由谁创建/分配 (管理员用户ID)
     */
    private Long assignedBy;

    /**
     * 分配时间
     */
    private OffsetDateTime assignedAt;

    /**
     * 备注
     */
    private String notes;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /**
     * 删除时间 (软删除)
     */
    private OffsetDateTime deletedAt;
}