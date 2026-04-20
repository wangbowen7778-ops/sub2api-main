package com.sub2api.module.billing.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 优惠码使用记录实体
 * 表名: promo_code_usages
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("promo_code_usages")
public class PromoCodeUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠码ID
     */
    private Long promoCodeId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 使用的优惠码
     */
    private String promoCode;

    /**
     * 赠送金额
     */
    private BigDecimal bonusAmount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
