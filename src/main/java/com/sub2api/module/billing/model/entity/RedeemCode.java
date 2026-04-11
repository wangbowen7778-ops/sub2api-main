package com.sub2api.module.billing.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 兑换码实体
 * 表名: redeem_codes
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("redeem_codes")
public class RedeemCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 兑换码ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 兑换码
     */
    private String code;

    /**
     * 类型: balance, subscription
     */
    private String type;

    /**
     * 价值
     */
    private BigDecimal value;

    /**
     * 状态: unused, used, expired
     */
    private String status;

    /**
     * 使用者用户ID
     */
    private Long usedBy;

    /**
     * 使用时间
     */
    private LocalDateTime usedAt;

    /**
     * 备注
     */
    private String notes;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 关联的分组ID
     */
    private Long groupId;

    /**
     * 有效期天数
     */
    private Integer validityDays;

    // Getters and Setters
    public Long getId() { return id; }
    public RedeemCode setId(Long id) { this.id = id; return this; }

    public String getCode() { return code; }
    public RedeemCode setCode(String code) { this.code = code; return this; }

    public String getType() { return type; }
    public RedeemCode setType(String type) { this.type = type; return this; }

    public BigDecimal getValue() { return value; }
    public RedeemCode setValue(BigDecimal value) { this.value = value; return this; }

    public String getStatus() { return status; }
    public RedeemCode setStatus(String status) { this.status = status; return this; }

    public Long getUsedBy() { return usedBy; }
    public RedeemCode setUsedBy(Long usedBy) { this.usedBy = usedBy; return this; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public RedeemCode setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; return this; }

    public String getNotes() { return notes; }
    public RedeemCode setNotes(String notes) { this.notes = notes; return this; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public RedeemCode setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

    public Long getGroupId() { return groupId; }
    public RedeemCode setGroupId(Long groupId) { this.groupId = groupId; return this; }

    public Integer getValidityDays() { return validityDays; }
    public RedeemCode setValidityDays(Integer validityDays) { this.validityDays = validityDays; return this; }
}
