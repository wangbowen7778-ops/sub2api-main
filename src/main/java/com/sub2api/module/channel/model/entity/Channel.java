package com.sub2api.module.channel.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 渠道实体
 * 表名: channels
 */
@Data
@Accessors(chain = true)
@TableName("channels")
public class Channel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 渠道ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 渠道名称
     */
    private String name;

    /**
     * 渠道描述
     */
    private String description;

    /**
     * 状态: active, disabled
     */
    private String status;

    /**
     * 计费模型来源: requested, upstream, channel_mapped
     */
    private String billingModelSource;

    /**
     * 是否限制模型（仅允许定价列表中的模型）
     */
    private Boolean restrictModels;

    /**
     * 模型映射 (JSON): platform -> {src -> dst}
     */
    private String modelMapping;

    /**
     * 关联的分组ID列表
     */
    @TableField(exist = false)
    private List<Long> groupIds;

    /**
     * 模型定价列表
     */
    @TableField(exist = false)
    private List<ChannelModelPricing> modelPricing;

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

    /**
     * 判断渠道是否启用
     */
    public boolean isActive() {
        return "active".equals(status);
    }
}
