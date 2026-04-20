package com.sub2api.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 用户实体
 * 表名: users
 *
 * @author Alibaba Java Code Guidelines
 */
@Accessors(chain = true)
@TableName("users")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码哈希
     */
    private String passwordHash;

    /**
     * 用户角色: user, admin
     */
    private String role;

    /**
     * 余额 (decimal(20,8))
     */
    private BigDecimal balance;

    /**
     * 并发限制
     */
    private Integer concurrency;

    /**
     * 用户状态: active, disabled
     */
    private String status;

    /**
     * 用户名
     */
    private String username;

    /**
     * 备注
     */
    private String notes;

    /**
     * TOTP 密钥 (加密存储)
     */
    private String totpSecretEncrypted;

    /**
     * 是否启用 TOTP
     */
    private Boolean totpEnabled;

    /**
     * TOTP 启用时间
     */
    private OffsetDateTime totpEnabledAt;

    /**
     * Token版本号 - 密码修改后递增使所有token失效
     */
    private Long tokenVersion;

    /**
     * 用户专属分组倍率配置 (map[groupId]rateMultiplier)
     * JSONB列，使用JacksonTypeHandler
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Long, Double> groupRates;

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

    // Getters and Setters
    public Long getId() { return id; }
    public User setId(Long id) { this.id = id; return this; }

    public String getEmail() { return email; }
    public User setEmail(String email) { this.email = email; return this; }

    public String getPasswordHash() { return passwordHash; }
    public User setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }

    public String getRole() { return role; }
    public User setRole(String role) { this.role = role; return this; }

    public BigDecimal getBalance() { return balance; }
    public User setBalance(BigDecimal balance) { this.balance = balance; return this; }

    public Integer getConcurrency() { return concurrency; }
    public User setConcurrency(Integer concurrency) { this.concurrency = concurrency; return this; }

    public String getStatus() { return status; }
    public User setStatus(String status) { this.status = status; return this; }

    public String getUsername() { return username; }
    public User setUsername(String username) { this.username = username; return this; }

    public String getNotes() { return notes; }
    public User setNotes(String notes) { this.notes = notes; return this; }

    public String getTotpSecretEncrypted() { return totpSecretEncrypted; }
    public User setTotpSecretEncrypted(String totpSecretEncrypted) { this.totpSecretEncrypted = totpSecretEncrypted; return this; }

    public Boolean getTotpEnabled() { return totpEnabled; }
    public User setTotpEnabled(Boolean totpEnabled) { this.totpEnabled = totpEnabled; return this; }

    public OffsetDateTime getTotpEnabledAt() { return totpEnabledAt; }
    public User setTotpEnabledAt(OffsetDateTime totpEnabledAt) { this.totpEnabledAt = totpEnabledAt; return this; }

    public Long getTokenVersion() { return tokenVersion; }
    public User setTokenVersion(Long tokenVersion) { this.tokenVersion = tokenVersion; return this; }

    public Map<Long, Double> getGroupRates() { return groupRates; }
    public User setGroupRates(Map<Long, Double> groupRates) { this.groupRates = groupRates; return this; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public User setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public User setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public User setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; return this; }

    /**
     * Set password (hashes the plaintext password using BCrypt)
     */
    public void setPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isBlank()) {
            return;
        }
        this.passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
                .encode(plaintextPassword);
    }
}
