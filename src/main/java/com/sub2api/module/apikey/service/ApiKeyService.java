package com.sub2api.module.apikey.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.service.GroupService;
import com.sub2api.module.apikey.mapper.ApiKeyMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.apikey.model.vo.CreateApiKeyRequest;
import com.sub2api.module.apikey.model.vo.UpdateApiKeyRequest;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.util.DateTimeUtil;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.model.entity.UserSubscription;
import com.sub2api.module.user.service.SubscriptionService;
import com.sub2api.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * API Key service
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService extends ServiceImpl<ApiKeyMapper, ApiKey> {

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheService apiKeyCacheService;
    private final UserService userService;
    private final GroupService groupService;
    private final SubscriptionService subscriptionService;

    // API Key status constants
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_QUOTA_EXHAUSTED = "quota_exhausted";
    public static final String STATUS_EXPIRED = "expired";

    // Rate limit window durations (in hours)
    private static final int RATE_LIMIT_WINDOW_5H = 5;
    private static final int RATE_LIMIT_WINDOW_1D = 24;
    private static final int RATE_LIMIT_WINDOW_7D = 168;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Create API Key (user-side)
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey createApiKey(Long userId, CreateApiKeyRequest request) {
        // Validate user exists
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String key;
        // Check if using custom key
        if (StringUtils.hasText(request.getCustomKey())) {
            // Validate custom key format
            validateCustomKey(request.getCustomKey());
            // Check if key already exists
            if (existsByKey(request.getCustomKey())) {
                throw new BusinessException(ErrorCode.DATA_EXISTS, "API Key already exists");
            }
            key = request.getCustomKey();
        } else {
            // Generate random API key
            key = generateKey();
        }

        // Validate group permission (if group is specified)
        if (request.getGroupId() != null && request.getGroupId() > 0) {
            Group group = groupService.findById(request.getGroupId());
            if (group == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Group not found");
            }
            if (!canUserBindGroup(user, group)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "User is not allowed to bind this group");
            }
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setKey(key);
        apiKey.setName(request.getName());
        apiKey.setGroupId(request.getGroupId());
        apiKey.setStatus(STATUS_ACTIVE);
        apiKey.setIpWhitelist(request.getIpWhitelist());
        apiKey.setIpBlacklist(request.getIpBlacklist());
        apiKey.setQuota(request.getQuota() != null ? request.getQuota() : BigDecimal.ZERO);
        apiKey.setQuotaUsed(BigDecimal.ZERO);
        apiKey.setRateLimit5h(request.getRateLimit5h() != null ? request.getRateLimit5h() : BigDecimal.ZERO);
        apiKey.setRateLimit1d(request.getRateLimit1d() != null ? request.getRateLimit1d() : BigDecimal.ZERO);
        apiKey.setRateLimit7d(request.getRateLimit7d() != null ? request.getRateLimit7d() : BigDecimal.ZERO);
        apiKey.setUsage5h(BigDecimal.ZERO);
        apiKey.setUsage1d(BigDecimal.ZERO);
        apiKey.setUsage7d(BigDecimal.ZERO);
        apiKey.setCreatedAt(OffsetDateTime.now());
        apiKey.setUpdatedAt(OffsetDateTime.now());

        // Set expiration time if specified
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            apiKey.setExpiresAt(OffsetDateTime.now().plusDays(request.getExpiresInDays()));
        }

        if (!save(apiKey)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create API Key");
        }

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(key);

        log.info("Created API Key: userId={}, keyId={}, name={}", userId, apiKey.getId(), request.getName());
        return apiKey;
    }

    /**
     * Admin create API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey adminCreateApiKey(Long userId, String name, Long groupId) {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName(name);
        request.setGroupId(groupId);
        return createApiKey(userId, request);
    }

    /**
     * Generate random API Key
     */
    private String generateKey() {
        String prefix = "sk-";
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + bytesToHex(bytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validate custom API Key format
     */
    private void validateCustomKey(String key) {
        if (key.length() < 16) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "API Key must be at least 16 characters");
        }
        // Only allow letters, numbers, underscores, and hyphens
        for (char c : key.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "API Key can only contain letters, numbers, underscores, and hyphens");
            }
        }
    }

    /**
     * Check if user can bind to specified group
     */
    private boolean canUserBindGroup(User user, Group group) {
        // Subscription type group: requires valid subscription
        if (group.getSubscriptionType() != null && "subscription".equals(group.getSubscriptionType())) {
            return subscriptionService.isSubscriptionValid(user.getId(), group.getId());
        }
        // Standard type group: use original AllowedGroups and IsExclusive logic
        return user.canBindGroup(group.getId(), group.getIsExclusive());
    }

    /**
     * Check if key already exists
     */
    public boolean existsByKey(String key) {
        return count(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKey, key)
                .isNull(ApiKey::getDeletedAt)) > 0;
    }

    /**
     * Validate API Key (for authentication)
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
        if (!STATUS_ACTIVE.equals(apiKey.getStatus())) {
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
     * Get API Key by key string (for authentication)
     */
    public ApiKey getByKey(String key) {
        return getOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKey, key)
                .isNull(ApiKey::getDeletedAt));
    }

    /**
     * Query API Keys by user ID (paginated)
     */
    public PageResult<ApiKey> listByUserId(Long userId, Integer page, Integer pageSize, String search, String status, Long groupId) {
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getUserId, userId)
                .isNull(ApiKey::getDeletedAt);

        if (StringUtils.hasText(search)) {
            wrapper.like(ApiKey::getName, search);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ApiKey::getStatus, status);
        }
        if (groupId != null) {
            if (groupId == 0) {
                wrapper.isNull(ApiKey::getGroupId);
            } else {
                wrapper.eq(ApiKey::getGroupId, groupId);
            }
        }

        wrapper.orderByDesc(ApiKey::getCreatedAt);

        Page<ApiKey> pageResult = page(new Page<>(page, pageSize), wrapper);
        return PageResult.of(pageResult.getTotal(), pageResult.getRecords(), pageResult.getCurrent(), pageResult.getSize());
    }

    /**
     * Query all API Keys by user ID (no pagination)
     */
    public List<ApiKey> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId)
                .isNull(ApiKey::getDeletedAt)
                .orderByDesc(ApiKey::getCreatedAt));
    }

    /**
     * Update API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey updateApiKey(Long keyId, Long userId, UpdateApiKeyRequest request) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // Validate ownership
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Not authorized to update this API Key");
        }

        // Update fields
        if (StringUtils.hasText(request.getName())) {
            apiKey.setName(request.getName());
        }

        if (request.getGroupId() != null) {
            // Validate group permission
            User user = userService.getById(userId);
            Group group = groupService.findById(request.getGroupId());
            if (group != null && !canUserBindGroup(user, group)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "User is not allowed to bind this group");
            }
            apiKey.setGroupId(request.getGroupId() == 0 ? null : request.getGroupId());
        }

        if (StringUtils.hasText(request.getStatus())) {
            apiKey.setStatus(request.getStatus());
        }

        // Update IP restrictions
        if (request.getIpWhitelist() != null) {
            apiKey.setIpWhitelist(request.getIpWhitelist());
        }
        if (request.getIpBlacklist() != null) {
            apiKey.setIpBlacklist(request.getIpBlacklist());
        }

        // Update quota
        if (request.getQuota() != null) {
            apiKey.setQuota(request.getQuota());
            // If quota increased and status was quota_exhausted, reactivate
            if (STATUS_QUOTA_EXHAUSTED.equals(apiKey.getStatus())
                    && apiKey.getQuotaUsed() != null
                    && request.getQuota().compareTo(apiKey.getQuotaUsed()) > 0) {
                apiKey.setStatus(STATUS_ACTIVE);
            }
        }
        if (Boolean.TRUE.equals(request.getResetQuota())) {
            apiKey.setQuotaUsed(BigDecimal.ZERO);
            if (STATUS_QUOTA_EXHAUSTED.equals(apiKey.getStatus())) {
                apiKey.setStatus(STATUS_ACTIVE);
            }
        }

        // Update expiration
        if (Boolean.TRUE.equals(request.getClearExpiration())) {
            apiKey.setExpiresAt(null);
            if (STATUS_EXPIRED.equals(apiKey.getStatus())) {
                apiKey.setStatus(STATUS_ACTIVE);
            }
        } else if (request.getExpiresAt() != null) {
            apiKey.setExpiresAt(request.getExpiresAt());
            if (STATUS_EXPIRED.equals(apiKey.getStatus())
                    && request.getExpiresAt().isAfter(OffsetDateTime.now())) {
                apiKey.setStatus(STATUS_ACTIVE);
            }
        }

        // Update rate limits
        if (request.getRateLimit5h() != null) {
            apiKey.setRateLimit5h(request.getRateLimit5h());
        }
        if (request.getRateLimit1d() != null) {
            apiKey.setRateLimit1d(request.getRateLimit1d());
        }
        if (request.getRateLimit7d() != null) {
            apiKey.setRateLimit7d(request.getRateLimit7d());
        }

        // Reset rate limit usage
        if (Boolean.TRUE.equals(request.getResetRateLimitUsage())) {
            apiKey.setUsage5h(BigDecimal.ZERO);
            apiKey.setUsage1d(BigDecimal.ZERO);
            apiKey.setUsage7d(BigDecimal.ZERO);
            apiKey.setWindow5hStart(null);
            apiKey.setWindow1dStart(null);
            apiKey.setWindow7dStart(null);
        }

        apiKey.setUpdatedAt(OffsetDateTime.now());

        if (!updateById(apiKey)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to update API Key");
        }

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(apiKey.getKey());

        log.info("Updated API Key: keyId={}, userId={}", keyId, userId);
        return apiKey;
    }

    /**
     * Delete API Key (user-side - soft delete)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long keyId, Long userId) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // Validate ownership
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Not authorized to delete this API Key");
        }

        // Soft delete
        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getDeletedAt, OffsetDateTime.now())
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(apiKey.getKey());

        log.info("Deleted API Key: keyId={}, userId={}", keyId, userId);
    }

    /**
     * Delete API Key (admin - soft delete)
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteApiKey(Long keyId) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // Soft delete
        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getDeletedAt, OffsetDateTime.now())
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(apiKey.getKey());

        log.info("Admin deleted API Key: keyId={}", keyId);
    }

    /**
     * Update API Key status (admin)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long keyId, String status) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getStatus, status)
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(apiKey.getKey());

        log.info("Updated API Key status: keyId={}, status={}", keyId, status);
    }

    /**
     * Admin update API Key group binding
     * groupId: null=no change, 0=unbind, >0=bind to target group
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUpdateGroupResult adminUpdateGroup(Long keyId, Long groupId) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        AdminUpdateGroupResult result = new AdminUpdateGroupResult();
        result.setApiKey(apiKey);

        if (groupId == null) {
            // null means no change
            return result;
        }

        if (groupId < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "group_id must be non-negative");
        }

        if (groupId == 0) {
            // 0 means unbind
            apiKey.setGroupId(null);
        } else {
            // Validate target group exists and status is active
            Group group = groupService.findById(groupId);
            if (group == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Group not found");
            }
            if (!"active".equals(group.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Target group is not active");
            }

            // Subscription type group: user must have valid subscription
            if (group.getSubscriptionType() != null && "subscription".equals(group.getSubscriptionType())) {
                // TODO: check if user has valid subscription
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Subscription required for this group type");
            }

            apiKey.setGroupId(groupId);

            // Exclusive standard group: need to add group permission to user
            // TODO: implement allowed_groups update logic
            if (Boolean.TRUE.equals(group.getIsExclusive()) && !("subscription".equals(group.getSubscriptionType()))) {
                result.setAutoGrantedGroupAccess(true);
                result.setGrantedGroupId(groupId);
                result.setGrantedGroupName(group.getName());
            }
        }

        apiKey.setUpdatedAt(OffsetDateTime.now());
        updateById(apiKey);

        // Invalidate auth cache
        apiKeyCacheService.deleteCache(apiKey.getKey());

        result.setApiKey(apiKey);
        log.info("Admin updated API Key group: keyId={}, groupId={}", keyId, groupId);
        return result;
    }

    /**
     * Admin update group result
     */
    @lombok.Data
    public static class AdminUpdateGroupResult {
        private ApiKey apiKey;
        private Boolean autoGrantedGroupAccess;
        private Long grantedGroupId;
        private String grantedGroupName;
    }

    /**
     * Update last used time (debounced)
     */
    @Transactional(rollbackFor = Exception.class)
    public void touchLastUsed(Long keyId) {
        if (keyId == null || keyId <= 0) {
            return;
        }

        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            return;
        }

        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getLastUsedAt, OffsetDateTime.now())
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);
    }

    /**
     * Add quota used
     */
    @Transactional(rollbackFor = Exception.class)
    public void addQuotaUsed(Long keyId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            return;
        }

        BigDecimal newQuotaUsed = apiKey.getQuotaUsed().add(amount);
        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getQuotaUsed, newQuotaUsed)
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());

        // If quota exhausted, update status
        if (apiKey.getQuota() != null && apiKey.getQuota().compareTo(BigDecimal.ZERO) > 0
                && newQuotaUsed.compareTo(apiKey.getQuota()) >= 0) {
            wrapper.set(ApiKey::getStatus, STATUS_QUOTA_EXHAUSTED);
        }

        update(wrapper);
    }

    /**
     * Check if API Key is expired
     */
    public boolean isExpired(ApiKey apiKey) {
        if (apiKey.getExpiresAt() == null) {
            return false;
        }
        return apiKey.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    /**
     * Check if API Key quota is exhausted
     */
    public boolean isQuotaExhausted(ApiKey apiKey) {
        if (apiKey.getQuota() == null || apiKey.getQuota().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return apiKey.getQuotaUsed().compareTo(apiKey.getQuota()) >= 0;
    }

    /**
     * Get available groups for user
     */
    public List<Group> getAvailableGroups(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        List<Group> allActiveGroups = groupService.listActive();
        List<Group> availableGroups = new ArrayList<>();

        for (Group group : allActiveGroups) {
            if (canUserBindGroup(user, group)) {
                availableGroups.add(group);
            }
        }

        return availableGroups;
    }

    /**
     * Get user group rates config
     * Returns Map<groupId, rateMultiplier>
     */
    public Map<Long, Double> getUserGroupRates(Long userId) {
        return userService.getGroupRates(userId);
    }
}
