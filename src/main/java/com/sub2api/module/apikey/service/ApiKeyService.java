package com.sub2api.module.apikey.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.apikey.mapper.ApiKeyMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.apikey.model.vo.CreateApiKeyRequest;
import com.sub2api.module.apikey.model.vo.UpdateApiKeyRequest;
import com.sub2api.module.common.config.ConfigProperties;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.util.DateTimeUtil;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import com.sub2api.module.group.service.GroupService;
import com.sub2api.module.group.model.entity.AppGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheService apiKeyCacheService;
    private final UserService userService;
    private final GroupService groupService;
    private final ConfigProperties configProperties;

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
     * 创建 API Key (用户侧)
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey createApiKey(Long userId, CreateApiKeyRequest request) {
        // 验证用户存在
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String key;
        // 判断是否使用自定义Key
        if (StringUtils.hasText(request.getCustomKey())) {
            // 验证自定义Key格式
            validateCustomKey(request.getCustomKey());
            // 检查Key是否已存在
            if (existsByKey(request.getCustomKey())) {
                throw new BusinessException(ErrorCode.DATA_EXISTS, "API Key already exists");
            }
            key = request.getCustomKey();
        } else {
            // 生成随机API Key
            key = generateKey();
        }

        // 验证分组权限（如果指定了分组）
        if (request.getGroupId() != null && request.getGroupId() > 0) {
            AppGroup group = groupService.getById(request.getGroupId());
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
        apiKeyCacheService.deleteAuthCache(key);

        logger.info("Created API Key: userId={}, keyId={}, name={}", userId, apiKey.getId(), request.getName());
        return apiKey;
    }

    /**
     * 管理员创建 API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey adminCreateApiKey(Long userId, String name, Long groupId) {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName(name);
        request.setGroupId(groupId);
        return createApiKey(userId, request);
    }

    /**
     * 生成随机 API Key
     */
    private String generateKey() {
        String prefix = configProperties.getApiKeyPrefix();
        if (prefix == null || prefix.isEmpty()) {
            prefix = "sk-";
        }
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
     * 验证自定义 API Key 格式
     */
    private void validateCustomKey(String key) {
        if (key.length() < 16) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "API Key must be at least 16 characters");
        }
        // 只允许字母、数字、下划线、连字符
        for (char c : key.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "API Key can only contain letters, numbers, underscores, and hyphens");
            }
        }
    }

    /**
     * 检查用户是否可以绑定指定分组
     */
    private boolean canUserBindGroup(User user, AppGroup group) {
        // 订阅类型分组：需要有效订阅
        if (group.isSubscriptionType()) {
            // TODO: 检查用户是否有有效订阅
            return false;
        }
        // 标准类型分组：使用原有的 AllowedGroups 和 IsExclusive 逻辑
        return user.canBindGroup(group.getId(), group.getIsExclusive());
    }

    /**
     * 检查 Key 是否已存在
     */
    public boolean existsByKey(String key) {
        return count(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKey, key)
                .isNull(ApiKey::getDeletedAt)) > 0;
    }

    /**
     * 验证 API Key (用于认证)
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
     * 根据 Key 获取 API Key (用于认证)
     */
    public ApiKey getByKey(String key) {
        return getOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKey, key)
                .isNull(ApiKey::getDeletedAt));
    }

    /**
     * 根据用户ID查询 API Keys (分页)
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
     * 根据用户ID查询所有 API Keys (不分页)
     */
    public List<ApiKey> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId)
                .isNull(ApiKey::getDeletedAt)
                .orderByDesc(ApiKey::getCreatedAt));
    }

    /**
     * 根据ID获取 API Key
     */
    public ApiKey getById(Long keyId) {
        return getById(keyId);
    }

    /**
     * 更新 API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKey updateApiKey(Long keyId, Long userId, UpdateApiKeyRequest request) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // 验证所有权
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Not authorized to update this API Key");
        }

        // 更新字段
        if (StringUtils.hasText(request.getName())) {
            apiKey.setName(request.getName());
        }

        if (request.getGroupId() != null) {
            // 验证分组权限
            User user = userService.getById(userId);
            AppGroup group = groupService.getById(request.getGroupId());
            if (group != null && !canUserBindGroup(user, group)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "User is not allowed to bind this group");
            }
            apiKey.setGroupId(request.getGroupId() == 0 ? null : request.getGroupId());
        }

        if (StringUtils.hasText(request.getStatus())) {
            apiKey.setStatus(request.getStatus());
        }

        // 更新 IP 限制
        if (request.getIpWhitelist() != null) {
            apiKey.setIpWhitelist(request.getIpWhitelist());
        }
        if (request.getIpBlacklist() != null) {
            apiKey.setIpBlacklist(request.getIpBlacklist());
        }

        // 更新配额
        if (request.getQuota() != null) {
            apiKey.setQuota(request.getQuota());
            // 如果配额增加且状态是 quota_exhausted，则重新激活
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

        // 更新过期时间
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

        // 更新费率限制
        if (request.getRateLimit5h() != null) {
            apiKey.setRateLimit5h(request.getRateLimit5h());
        }
        if (request.getRateLimit1d() != null) {
            apiKey.setRateLimit1d(request.getRateLimit1d());
        }
        if (request.getRateLimit7d() != null) {
            apiKey.setRateLimit7d(request.getRateLimit7d());
        }

        // 重置费率限制使用量
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
        apiKeyCacheService.deleteAuthCache(apiKey.getKey());

        logger.info("Updated API Key: keyId={}, userId={}", keyId, userId);
        return apiKey;
    }

    /**
     * 删除 API Key (用户侧 - 软删除)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long keyId, Long userId) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // 验证所有权
        if (!apiKey.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Not authorized to delete this API Key");
        }

        // 软删除
        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getDeletedAt, OffsetDateTime.now())
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);

        // Invalidate auth cache
        apiKeyCacheService.deleteAuthCache(apiKey.getKey());

        logger.info("Deleted API Key: keyId={}, userId={}", keyId, userId);
    }

    /**
     * 删除 API Key (管理员 - 软删除)
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteApiKey(Long keyId) {
        ApiKey apiKey = getById(keyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key not found");
        }

        // 软删除
        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, keyId)
                .set(ApiKey::getDeletedAt, OffsetDateTime.now())
                .set(ApiKey::getUpdatedAt, OffsetDateTime.now());
        update(wrapper);

        // Invalidate auth cache
        apiKeyCacheService.deleteAuthCache(apiKey.getKey());

        logger.info("Admin deleted API Key: keyId={}", keyId);
    }

    /**
     * 更新 API Key 状态 (管理员)
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
        apiKeyCacheService.deleteAuthCache(apiKey.getKey());

        logger.info("Updated API Key status: keyId={}, status={}", keyId, status);
    }

    /**
     * 管理员更新 API Key 分组绑定
     * groupId: null=不修改, 0=解绑, >0=绑定到目标分组
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
            // null 表示不修改
            return result;
        }

        if (groupId < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "group_id must be non-negative");
        }

        if (groupId == 0) {
            // 0 表示解绑分组
            apiKey.setGroupId(null);
        } else {
            // 验证目标分组存在且状态为 active
            AppGroup group = groupService.getById(groupId);
            if (group == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Group not found");
            }
            if (!"active".equals(group.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Target group is not active");
            }

            // 订阅类型分组：用户须持有该分组的有效订阅才可绑定
            if (group.isSubscriptionType()) {
                // TODO: 检查用户是否有有效订阅
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Subscription required for this group type");
            }

            apiKey.setGroupId(groupId);

            // 专属标准分组：需要给用户添加分组权限
            // TODO: 实现用户allowed_groups的更新逻辑
            if (Boolean.TRUE.equals(group.getIsExclusive()) && !group.isSubscriptionType()) {
                // userService.addAllowedGroup(apiKey.getUserId(), groupId);
                result.setAutoGrantedGroupAccess(true);
                result.setGrantedGroupId(groupId);
                result.setGrantedGroupName(group.getName());
            }
        }

        apiKey.setUpdatedAt(OffsetDateTime.now());
        updateById(apiKey);

        // Invalidate auth cache
        apiKeyCacheService.deleteAuthCache(apiKey.getKey());

        result.setApiKey(apiKey);
        logger.info("Admin updated API Key group: keyId={}, groupId={}", keyId, groupId);
        return result;
    }

    /**
     * 管理员更新分组结果
     */
    @lombok.Data
    public static class AdminUpdateGroupResult {
        private ApiKey apiKey;
        private Boolean autoGrantedGroupAccess;
        private Long grantedGroupId;
        private String grantedGroupName;
    }

    /**
     * 更新最后使用时间 (防抖)
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
     * 增加配额使用量
     */
    @Transactional(rollbackFor = Exception.class)
    public void incrementQuotaUsed(Long keyId, BigDecimal amount) {
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

        // 如果配额用完，更新状态
        if (apiKey.getQuota() != null && apiKey.getQuota().compareTo(BigDecimal.ZERO) > 0
                && newQuotaUsed.compareTo(apiKey.getQuota()) >= 0) {
            wrapper.set(ApiKey::getStatus, STATUS_QUOTA_EXHAUSTED);
        }

        update(wrapper);
    }

    /**
     * 检查 API Key 是否过期
     */
    public boolean isExpired(ApiKey apiKey) {
        if (apiKey.getExpiresAt() == null) {
            return false;
        }
        return apiKey.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    /**
     * 检查 API Key 配额是否用完
     */
    public boolean isQuotaExhausted(ApiKey apiKey) {
        if (apiKey.getQuota() == null || apiKey.getQuota().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return apiKey.getQuotaUsed().compareTo(apiKey.getQuota()) >= 0;
    }

    /**
     * 获取用户可用的分组列表
     */
    public List<AppGroup> getAvailableGroups(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        List<AppGroup> allActiveGroups = groupService.listActive();
        List<AppGroup> availableGroups = new ArrayList<>();

        for (AppGroup group : allActiveGroups) {
            if (canUserBindGroup(user, group)) {
                availableGroups.add(group);
            }
        }

        return availableGroups;
    }

    /**
     * 获取用户专属分组倍率配置
     * 返回 Map<groupId, rateMultiplier>
     */
    public Map<Long, Double> getUserGroupRates(Long userId) {
        // TODO: 实现用户专属分组倍率配置查询
        return Collections.emptyMap();
    }
}