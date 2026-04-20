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
 * 渠道模型定价实体
 * 表名: channel_model_pricing
 */
@Data
@Accessors(chain = true)
@TableName("channel_model_pricing")
public class ChannelModelPricing implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 定价ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 渠道ID
     */
    private Long channelId;

    /**
     * 所属平台: anthropic, openai, google, antigravity
     */
    private String platform;

    /**
     * 绑定的模型列表
     */
    private String models;

    /**
     * 计费模式: token, per_request, image
     */
    private String billingMode;

    /**
     * 每 token 输入价格（USD）- 向后兼容 flat 定价
     */
    private BigDecimal inputPrice;

    /**
     * 每 token 输出价格（USD）
     */
    private BigDecimal outputPrice;

    /**
     * 缓存写入价格
     */
    private BigDecimal cacheWritePrice;

    /**
     * 缓存读取价格
     */
    private BigDecimal cacheReadPrice;

    /**
     * 图片输出价格
     */
    private BigDecimal imageOutputPrice;

    /**
     * 默认按次计费价格（USD）
     */
    private BigDecimal perRequestPrice;

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

    // ========== 非数据库字段 ==========

    /**
     * 区间定价列表
     */
    @TableField(exist = false)
    private List<PricingInterval> intervals;

    // ========== 辅助方法 ==========

    public List<String> getModelList() {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        return List.of(models.split(","));
    }

    public void setModelList(List<String> modelList) {
        if (modelList == null || modelList.isEmpty()) {
            this.models = "";
        } else {
            this.models = String.join(",", modelList);
        }
    }

    public ChannelModelPricing deepClone() {
        ChannelModelPricing clone = new ChannelModelPricing();
        clone.id = this.id;
        clone.channelId = this.channelId;
        clone.platform = this.platform;
        clone.models = this.models;
        clone.billingMode = this.billingMode;
        clone.inputPrice = this.inputPrice;
        clone.outputPrice = this.outputPrice;
        clone.cacheWritePrice = this.cacheWritePrice;
        clone.cacheReadPrice = this.cacheReadPrice;
        clone.imageOutputPrice = this.imageOutputPrice;
        clone.perRequestPrice = this.perRequestPrice;
        clone.createdAt = this.createdAt;
        clone.updatedAt = this.updatedAt;
        if (this.intervals != null) {
            clone.intervals = this.intervals.stream()
                    .map(PricingInterval::deepClone)
                    .toList();
        }
        return clone;
    }
}
