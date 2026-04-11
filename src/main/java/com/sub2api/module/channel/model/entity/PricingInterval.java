package com.sub2api.module.channel.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定价区间实体
 * 表名: pricing_intervals
 */
@Data
@Accessors(chain = true)
@TableName("pricing_intervals")
public class PricingInterval implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 区间ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 定价ID
     */
    private Long pricingId;

    /**
     * 区间下界（含）
     */
    private Integer minTokens;

    /**
     * 区间上界（不含），null = 无上限
     */
    private Integer maxTokens;

    /**
     * 层级标签（按次/图片模式：1K, 2K, 4K, HD 等）
     */
    private String tierLabel;

    /**
     * token 模式：每 token 输入价
     */
    private BigDecimal inputPrice;

    /**
     * token 模式：每 token 输出价
     */
    private BigDecimal outputPrice;

    /**
     * token 模式：缓存写入价
     */
    private BigDecimal cacheWritePrice;

    /**
     * token 模式：缓存读取价
     */
    private BigDecimal cacheReadPrice;

    /**
     * 按次/图片模式：每次请求价格
     */
    private BigDecimal perRequestPrice;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

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

    public PricingInterval deepClone() {
        PricingInterval clone = new PricingInterval();
        clone.id = this.id;
        clone.pricingId = this.pricingId;
        clone.minTokens = this.minTokens;
        clone.maxTokens = this.maxTokens;
        clone.tierLabel = this.tierLabel;
        clone.inputPrice = this.inputPrice;
        clone.outputPrice = this.outputPrice;
        clone.cacheWritePrice = this.cacheWritePrice;
        clone.cacheReadPrice = this.cacheReadPrice;
        clone.perRequestPrice = this.perRequestPrice;
        clone.sortOrder = this.sortOrder;
        clone.createdAt = this.createdAt;
        clone.updatedAt = this.updatedAt;
        return clone;
    }
}
