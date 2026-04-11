package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时测试计划实体
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("scheduled_test_plans")
public class ScheduledTestPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 计划名称
     */
    private String name;

    /**
     * Cron 表达式
     */
    private String cronExpression;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 测试提示词
     */
    private String prompt;

    /**
     * 最大结果数
     */
    private Integer maxResults;

    /**
     * 下次执行时间
     */
    private LocalDateTime nextRunAt;

    /**
     * 最近执行时间
     */
    private LocalDateTime lastRunAt;

    /**
     * 状态: active, paused, disabled
     */
    private String status;

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
     * 删除时间
     */
    private LocalDateTime deletedAt;
}
