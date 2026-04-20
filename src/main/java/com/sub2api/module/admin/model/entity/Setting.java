package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 系统设置实体
 * 表名: settings
 * 实际列: id, key, value, updated_at
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
    private String key;

    /**
     * 设置值
     */
    private String value;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
