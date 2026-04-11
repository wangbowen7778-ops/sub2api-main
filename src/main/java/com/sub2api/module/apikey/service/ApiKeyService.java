package com.sub2api.module.apikey.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.apikey.mapper.ApiKeyMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService extends ServiceImpl<ApiKeyMapper, ApiKey> {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheService apiKeyCacheService;

    /**
     * Create API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey createApiKey(Long userId, String name, Long groupId) {
        // Generate random Key
        String rawKey = "sk-" + IdUtil.fastSimpleUUID();

        ApiKey apiKey = new ApiKey();
        apiKey.setKey(rawKey);
        apiKey.setUserId(userId);
        apiKey.setName(name);
        apiKey.setGroupId(groupId);
        apiKey.setStatus("active");
        apiKey.setQuota(BigDecimal.ZERO);
        apiKey.setQuotaUsed(BigDecimal.ZERO);
        apiKey.setRateLimit5h(BigDecimal.ZERO);
        apiKey.setRateLimit1d(BigDecimal.ZERO);
        apiKey.setRateLimit7d(BigDecimal.ZERO);
        apiKey.setUsage5h(BigDecimal.ZERO);
        apiKey.setUsage1d(BigDecimal.ZERO);
        apiKey.setUsage7d(BigDecimal.ZERO);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());

        if (!save(apiKey)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create API Key");
        }

        log.info("Created API Key: userId={}, keyId={}, name={}", userId, apiKey.getId(), name);

        // Return raw Key
        return apiKey;
    }

    /**
     * Validate API Key
     */
    public ApiKeyInfo validateApiKey(String rawKey) {
        // First get from cache
        ApiKeyInfo cached = apiKeyCacheService.getApiKeyInfo(rawKey);
        if (cached != null) {
            return cached;
        }

        // Query from database
        ApiKey apiKey = getOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKey, rawKey)
                .isNull(ApiKey::getDeletedAt));

        if (apiKey == null) {
            return null;
        }

        // Check status
        if (!"active".equals(apiKey.getStatus())) {
            throw new BusinessException(ErrorCode.API_KEY_DISABLED);
        }

        // Check expiration
        if (apiKey.getExpiresAt() != null && DateTimeUtil.isExpired(apiKey.getExpiresAt())) {
            throw new BusinessException(ErrorCode.API_KEY_EXPIRED);
        }

        // Convert to ApiKeyInfo
        ApiKeyInfo info = ApiKeyInfo.builder()
                .keyId(apiKey.getId())
                .userId(apiKey.getUserId())
                .keyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())) + "...")
                .groupId(apiKey.getGroupId())
                .status(apiKey.getStatus())
                .expireAt(apiKey.getExpiresAt())
                .rateLimit(apiKey.getRateLimit1d() != null ? apiKey.getRateLimit1d().intValue() : 0)
                .build();

        // Cache
        apiKeyCacheService.cacheApiKeyInfo(rawKey, info);

        return info;
    }

    /**
     * Query API Keys by user ID
     */
    public List<ApiKey> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId)
                .isNull(ApiKey::getDeletedAt)
                .orderByDesc(ApiKey::getCreatedAt));
    }

    /**
     * Update API Key status
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long keyId, String status) {
        ApiKey updateKey = new ApiKey();
        updateKey.setId(keyId);
        updateKey.setStatus(status);
        updateKey.setUpdatedAt(LocalDateTime.now());
        updateById(updateKey);
        log.info("Updated API Key status: keyId={}, status={}", keyId, status);
    }

    /**
     * Update last used time
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLastUsed(Long keyId) {
        ApiKey updateKey = new ApiKey();
        updateKey.setId(keyId);
        updateKey.setLastUsedAt(LocalDateTime.now());
        updateKey.setUpdatedAt(LocalDateTime.now());
        updateById(updateKey);
    }

    /**
     * Add quota used
     */
    @Transactional(rollbackFor = Exception.class)
    public void addQuotaUsed(Long keyId, BigDecimal amount) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            return;
        }

        ApiKey updateKey = new ApiKey();
        updateKey.setId(keyId);
        updateKey.setQuotaUsed(apiKey.getQuotaUsed().add(amount));
        updateKey.setUpdatedAt(LocalDateTime.now());
        updateById(updateKey);
    }

    /**
     * Soft delete API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long keyId) {
        ApiKey updateKey = new ApiKey();
        updateKey.setId(keyId);
        updateKey.setDeletedAt(LocalDateTime.now());
        updateKey.setUpdatedAt(LocalDateTime.now());
        updateById(updateKey);
        log.info("Deleted API Key: keyId={}", keyId);
    }
}
