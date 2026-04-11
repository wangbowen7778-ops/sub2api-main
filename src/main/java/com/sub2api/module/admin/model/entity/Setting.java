package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统设置实体
 * 表名: settings
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("settings")
public class Setting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设置ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设置键
     */
    private String settingKey;

    /**
     * 设置值
     */
    private String settingValue;

    /**
     * 设置类型: string, number, boolean, json
     */
    private String settingType;

    /**
     * 设置分组
     */
    private String category;

    /**
     * 设置描述
     */
    private String description;

    /**
     * 是否可编辑
     */
    private Boolean editable;

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
