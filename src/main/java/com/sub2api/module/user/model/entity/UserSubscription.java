package com.sub2api.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
     * 订阅类型: standard, pro, enterprise
     */
    private String subscriptionType;

    /**
     * 关联的分组ID
     */
    private Long groupId;

    /**
     * 状态: active, expired, cancelled
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 到期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 删除时间 (软删除)
     */
    private LocalDateTime deletedAt;
}
