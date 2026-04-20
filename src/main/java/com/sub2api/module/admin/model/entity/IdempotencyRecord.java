package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 幂等性记录实体
 * 表名: idempotency_records
 *
 * 用于确保重复请求的幂等性处理
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("idempotency_records")
public class IdempotencyRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 作用域 (如 "user", "admin", "api" 等)
     */
    private String scope;

    /**
     * 幂等性键的哈希值
     */
    private String idempotencyKeyHash;

    /**
     * 请求指纹 (用于检测相同 payload 的重复请求)
     */
    private String requestFingerprint;

    /**
     * 状态: processing, succeeded, failed_retryable
     */
    private String status;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 响应体 (JSON 格式)
     */
    private String responseBody;

    /**
     * 错误原因
     */
    private String errorReason;

    /**
     * 锁定直到 (用于防止并发处理)
     */
    private OffsetDateTime lockedUntil;

    /**
     * 过期时间
     */
    private OffsetDateTime expiresAt;

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

    // Status constants
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED_RETRYABLE = "failed_retryable";

    // Getters and Setters
    public Long getId() { return id; }
    public IdempotencyRecord setId(Long id) { this.id = id; return this; }

    public String getScope() { return scope; }
    public IdempotencyRecord setScope(String scope) { this.scope = scope; return this; }

    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public IdempotencyRecord setIdempotencyKeyHash(String idempotencyKeyHash) { this.idempotencyKeyHash = idempotencyKeyHash; return this; }

    public String getRequestFingerprint() { return requestFingerprint; }
    public IdempotencyRecord setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; return this; }

    public String getStatus() { return status; }
    public IdempotencyRecord setStatus(String status) { this.status = status; return this; }

    public Integer getResponseStatus() { return responseStatus; }
    public IdempotencyRecord setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; return this; }

    public String getResponseBody() { return responseBody; }
    public IdempotencyRecord setResponseBody(String responseBody) { this.responseBody = responseBody; return this; }

    public String getErrorReason() { return errorReason; }
    public IdempotencyRecord setErrorReason(String errorReason) { this.errorReason = errorReason; return this; }

    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public IdempotencyRecord setLockedUntil(OffsetDateTime lockedUntil) { this.lockedUntil = lockedUntil; return this; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public IdempotencyRecord setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; return this; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public IdempotencyRecord setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public IdempotencyRecord setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

    /**
     * 检查记录是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(java.time.OffsetDateTime.now());
    }

    /**
     * 检查记录是否正在处理中
     */
    public boolean isProcessing() {
        return STATUS_PROCESSING.equals(status);
    }

    /**
     * 检查记录是否成功完成
     */
    public boolean isSucceeded() {
        return STATUS_SUCCEEDED.equals(status);
    }

    /**
     * 检查记录是否可重试失败
     */
    public boolean isFailedRetryable() {
        return STATUS_FAILED_RETRYABLE.equals(status);
    }

    /**
     * 检查是否被锁定
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(java.time.OffsetDateTime.now());
    }
}
