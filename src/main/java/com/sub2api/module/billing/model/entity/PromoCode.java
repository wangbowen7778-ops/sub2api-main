package com.sub2api.module.billing.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 优惠码实体
 * 表名: promo_codes
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("promo_codes")
public class PromoCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 优惠码ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠码
     */
    private String code;

    /**
     * 赠送余额金额
     */
    private BigDecimal bonusAmount;

    /**
     * 最大使用次数 (0 = 无限制)
     */
    private Integer maxUses;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 状态: active, disabled
     */
    private String status;

    /**
     * 过期时间
     */
    private OffsetDateTime expiresAt;

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
}
