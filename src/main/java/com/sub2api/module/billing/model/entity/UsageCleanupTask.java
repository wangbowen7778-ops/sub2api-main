package com.sub2api.module.billing.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 使用记录清理任务实体
 * 表名: usage_cleanup_tasks
 *
 * @author Sub2API
 */
@Data
@Accessors(chain = true)
@TableName("usage_cleanup_tasks")
public class UsageCleanupTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 任务状态: pending, running, succeeded, failed, canceled
     */
    private String status;

    /**
     * 过滤条件 (JSONB)
     */
    private String filters;

    /**
     * 创建人
     */
    private Long createdBy;

    /**
     * 已删除行数
     */
    private Long deletedRows;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 取消人
     */
    private Long canceledBy;

    /**
     * 取消时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime canceledAt;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
