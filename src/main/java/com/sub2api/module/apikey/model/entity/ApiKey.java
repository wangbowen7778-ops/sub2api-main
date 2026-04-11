package com.sub2api.module.apikey.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 实体
 * 表名: api_keys
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("api_keys")
public class ApiKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * API Key ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Key 值 (唯一)
     */
    private String key;

    /**
     * 名称
     */
    private String name;

    /**
     * 关联的分组ID
     */
    private Long groupId;

    /**
     * 状态: active, disabled
     */
    private String status;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * IP 白名单 (JSONB)
     */
    private List<String> ipWhitelist;

    /**
     * IP 黑名单 (JSONB)
     */
    private List<String> ipBlacklist;

    /**
     * 配额限额 (USD, 0 = 无限制)
     */
    private BigDecimal quota;

    /**
     * 已使用配额
     */
    private BigDecimal quotaUsed;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 5小时费率限制 (USD)
     */
    private BigDecimal rateLimit5h;

    /**
     * 每日费率限制 (USD)
     */
    private BigDecimal rateLimit1d;

    /**
     * 每周费率限制 (USD)
     */
    private BigDecimal rateLimit7d;

    /**
     * 当前 5h 使用量
     */
    private BigDecimal usage5h;

    /**
     * 当前 1d 使用量
     */
    private BigDecimal usage1d;

    /**
     * 当前 7d 使用量
     */
    private BigDecimal usage7d;

    /**
     * 5h 窗口开始时间
     */
    private LocalDateTime window5hStart;

    /**
     * 1d 窗口开始时间
     */
    private LocalDateTime window1dStart;

    /**
     * 7d 窗口开始时间
     */
    private LocalDateTime window7dStart;

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

    // Getters and Setters
    public Long getId() { return id; }
    public ApiKey setId(Long id) { this.id = id; return this; }

    public Long getUserId() { return userId; }
    public ApiKey setUserId(Long userId) { this.userId = userId; return this; }

    public String getKey() { return key; }
    public ApiKey setKey(String key) { this.key = key; return this; }

    public String getName() { return name; }
    public ApiKey setName(String name) { this.name = name; return this; }

    public Long getGroupId() { return groupId; }
    public ApiKey setGroupId(Long groupId) { this.groupId = groupId; return this; }

    public String getStatus() { return status; }
    public ApiKey setStatus(String status) { this.status = status; return this; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public ApiKey setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }

    public List<String> getIpWhitelist() { return ipWhitelist; }
    public ApiKey setIpWhitelist(List<String> ipWhitelist) { this.ipWhitelist = ipWhitelist; return this; }

    public List<String> getIpBlacklist() { return ipBlacklist; }
    public ApiKey setIpBlacklist(List<String> ipBlacklist) { this.ipBlacklist = ipBlacklist; return this; }

    public BigDecimal getQuota() { return quota; }
    public ApiKey setQuota(BigDecimal quota) { this.quota = quota; return this; }

    public BigDecimal getQuotaUsed() { return quotaUsed; }
    public ApiKey setQuotaUsed(BigDecimal quotaUsed) { this.quotaUsed = quotaUsed; return this; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public ApiKey setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }

    public BigDecimal getRateLimit5h() { return rateLimit5h; }
    public ApiKey setRateLimit5h(BigDecimal rateLimit5h) { this.rateLimit5h = rateLimit5h; return this; }

    public BigDecimal getRateLimit1d() { return rateLimit1d; }
    public ApiKey setRateLimit1d(BigDecimal rateLimit1d) { this.rateLimit1d = rateLimit1d; return this; }

    public BigDecimal getRateLimit7d() { return rateLimit7d; }
    public ApiKey setRateLimit7d(BigDecimal rateLimit7d) { this.rateLimit7d = rateLimit7d; return this; }

    public BigDecimal getUsage5h() { return usage5h; }
    public ApiKey setUsage5h(BigDecimal usage5h) { this.usage5h = usage5h; return this; }

    public BigDecimal getUsage1d() { return usage1d; }
    public ApiKey setUsage1d(BigDecimal usage1d) { this.usage1d = usage1d; return this; }

    public BigDecimal getUsage7d() { return usage7d; }
    public ApiKey setUsage7d(BigDecimal usage7d) { this.usage7d = usage7d; return this; }

    public LocalDateTime getWindow5hStart() { return window5hStart; }
    public ApiKey setWindow5hStart(LocalDateTime window5hStart) { this.window5hStart = window5hStart; return this; }

    public LocalDateTime getWindow1dStart() { return window1dStart; }
    public ApiKey setWindow1dStart(LocalDateTime window1dStart) { this.window1dStart = window1dStart; return this; }

    public LocalDateTime getWindow7dStart() { return window7dStart; }
    public ApiKey setWindow7dStart(LocalDateTime window7dStart) { this.window7dStart = window7dStart; return this; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public ApiKey setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ApiKey setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public ApiKey setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }
}
