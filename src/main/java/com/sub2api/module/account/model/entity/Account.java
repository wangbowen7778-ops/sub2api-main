package com.sub2api.module.account.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 账号实体
 * 表名: accounts
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("accounts")
public class Account implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 账户显示名称
     */
    private String name;

    /**
     * 备注
     */
    private String notes;

    /**
     * 所属平台: claude, gemini, openai, antigravity 等
     */
    private String platform;

    /**
     * 认证类型: api_key, oauth, cookie 等
     */
    private String type;

    /**
     * 认证凭证 (JSONB 格式存储)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials;

    /**
     * 扩展数据 (JSONB)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * 关联的代理配置ID
     */
    private Long proxyId;

    /**
     * 最大并发请求数
     */
    private Integer concurrency;

    /**
     * 负载因子
     */
    private Integer loadFactor;

    /**
     * 优先级 (数值越小优先级越高)
     */
    private Integer priority;

    /**
     * 账号计费倍率
     */
    private BigDecimal rateMultiplier;

    /**
     * 账户状态: active, error, disabled
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;

    /**
     * 账户过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 过期后自动暂停调度
     */
    private Boolean autoPauseOnExpired;

    /**
     * 是否可被调度器选中
     */
    private Boolean schedulable;

    /**
     * 触发速率限制的时间
     */
    private LocalDateTime rateLimitedAt;

    /**
     * 速率限制预计解除时间
     */
    private LocalDateTime rateLimitResetAt;

    /**
     * 过载状态解除时间
     */
    private LocalDateTime overloadUntil;

    /**
     * 临时不可调度状态解除时间
     */
    private LocalDateTime tempUnschedulableUntil;

    /**
     * 临时不可调度原因
     */
    private String tempUnschedulableReason;

    /**
     * 会话窗口开始时间
     */
    private LocalDateTime sessionWindowStart;

    /**
     * 会话窗口结束时间
     */
    private LocalDateTime sessionWindowEnd;

    /**
     * 会话窗口状态
     */
    private String sessionWindowStatus;

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
    public Account setId(Long id) { this.id = id; return this; }

    public String getName() { return name; }
    public Account setName(String name) { this.name = name; return this; }

    public String getNotes() { return notes; }
    public Account setNotes(String notes) { this.notes = notes; return this; }

    public String getPlatform() { return platform; }
    public Account setPlatform(String platform) { this.platform = platform; return this; }

    public String getType() { return type; }
    public Account setType(String type) { this.type = type; return this; }

    public Map<String, Object> getCredentials() { return credentials; }
    public Account setCredentials(Map<String, Object> credentials) { this.credentials = credentials; return this; }

    public Map<String, Object> getExtra() { return extra; }
    public Account setExtra(Map<String, Object> extra) { this.extra = extra; return this; }

    public Long getProxyId() { return proxyId; }
    public Account setProxyId(Long proxyId) { this.proxyId = proxyId; return this; }

    public Integer getConcurrency() { return concurrency; }
    public Account setConcurrency(Integer concurrency) { this.concurrency = concurrency; return this; }

    public Integer getLoadFactor() { return loadFactor; }
    public Account setLoadFactor(Integer loadFactor) { this.loadFactor = loadFactor; return this; }

    public Integer getPriority() { return priority; }
    public Account setPriority(Integer priority) { this.priority = priority; return this; }

    public BigDecimal getRateMultiplier() { return rateMultiplier; }
    public Account setRateMultiplier(BigDecimal rateMultiplier) { this.rateMultiplier = rateMultiplier; return this; }

    public String getStatus() { return status; }
    public Account setStatus(String status) { this.status = status; return this; }

    public String getErrorMessage() { return errorMessage; }
    public Account setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public Account setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Account setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }

    public Boolean getAutoPauseOnExpired() { return autoPauseOnExpired; }
    public Account setAutoPauseOnExpired(Boolean autoPauseOnExpired) { this.autoPauseOnExpired = autoPauseOnExpired; return this; }

    public Boolean getSchedulable() { return schedulable; }
    public Account setSchedulable(Boolean schedulable) { this.schedulable = schedulable; return this; }

    public LocalDateTime getRateLimitedAt() { return rateLimitedAt; }
    public Account setRateLimitedAt(LocalDateTime rateLimitedAt) { this.rateLimitedAt = rateLimitedAt; return this; }

    public LocalDateTime getRateLimitResetAt() { return rateLimitResetAt; }
    public Account setRateLimitResetAt(LocalDateTime rateLimitResetAt) { this.rateLimitResetAt = rateLimitResetAt; return this; }

    public LocalDateTime getOverloadUntil() { return overloadUntil; }
    public Account setOverloadUntil(LocalDateTime overloadUntil) { this.overloadUntil = overloadUntil; return this; }

    public LocalDateTime getTempUnschedulableUntil() { return tempUnschedulableUntil; }
    public Account setTempUnschedulableUntil(LocalDateTime tempUnschedulableUntil) { this.tempUnschedulableUntil = tempUnschedulableUntil; return this; }

    public String getTempUnschedulableReason() { return tempUnschedulableReason; }
    public Account setTempUnschedulableReason(String tempUnschedulableReason) { this.tempUnschedulableReason = tempUnschedulableReason; return this; }

    public LocalDateTime getSessionWindowStart() { return sessionWindowStart; }
    public Account setSessionWindowStart(LocalDateTime sessionWindowStart) { this.sessionWindowStart = sessionWindowStart; return this; }

    public LocalDateTime getSessionWindowEnd() { return sessionWindowEnd; }
    public Account setSessionWindowEnd(LocalDateTime sessionWindowEnd) { this.sessionWindowEnd = sessionWindowEnd; return this; }

    public String getSessionWindowStatus() { return sessionWindowStatus; }
    public Account setSessionWindowStatus(String sessionWindowStatus) { this.sessionWindowStatus = sessionWindowStatus; return this; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public Account setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Account setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public Account setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }
}
