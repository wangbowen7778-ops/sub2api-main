package com.sub2api.module.apikey.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * API Key info (for authentication context)
 *
 * @author Alibaba Java Code Guidelines
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Key ID
     */
    private Long keyId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Key prefix
     */
    private String keyPrefix;

    /**
     * Associated group ID
     */
    private Long groupId;

    /**
     * Group IDs (comma separated)
     */
    private String groupIds;

    /**
     * Scope
     */
    private String scope;

    /**
     * Status
     */
    private String status;

    /**
     * Expiration time
     */
    private OffsetDateTime expireAt;

    /**
     * Rate limit
     */
    private Integer rateLimit;

    // Getters and Setters
    public Long getKeyId() { return keyId; }
    public void setKeyId(Long keyId) { this.keyId = keyId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupIds() { return groupIds; }
    public void setGroupIds(String groupIds) { this.groupIds = groupIds; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(OffsetDateTime expireAt) { this.expireAt = expireAt; }

    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }

    /**
     * Is enabled
     */
    public boolean isEnabled() {
        return "active".equals(status) && (expireAt == null || expireAt.isAfter(OffsetDateTime.now()));
    }
}
