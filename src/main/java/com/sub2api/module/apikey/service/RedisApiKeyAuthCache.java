package com.sub2api.module.apikey.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis API Key 认证缓存
 * 提供 API Key 认证所需的快照数据缓存
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisApiKeyAuthCache {

    private final StringRedisTemplate redisTemplate;

    private static final String API_KEY_AUTH_CACHE_PREFIX = "apikey:auth:";
    private static final long API_KEY_AUTH_CACHE_TTL_SECONDS = 300; // 5 minutes

    /**
     * API Key 认证快照
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class APIKeyAuthSnapshot {
        @JsonProperty("api_key_id")
        private Long apiKeyId;

        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("group_id")
        private Long groupId;

        private String status;

        @JsonProperty("ip_whitelist")
        private List<String> ipWhitelist;

        @JsonProperty("ip_blacklist")
        private List<String> ipBlacklist;

        private UserSnapshot user;
        private GroupSnapshot group;

        // Quota fields
        private double quota;
        private double quotaUsed;

        // Expiration
        @JsonProperty("expires_at")
        private String expiresAt;

        // Rate limit configuration
        @JsonProperty("rate_limit_5h")
        private double rateLimit5h;

        @JsonProperty("rate_limit_1d")
        private double rateLimit1d;

        @JsonProperty("rate_limit_7d")
        private double rateLimit7d;
    }

    /**
     * 用户快照
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserSnapshot {
        private Long id;
        private String status;
        private String role;
        private BigDecimal balance;
        private Integer concurrency;
    }

    /**
     * 分组快照
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupSnapshot {
        private Long id;
        private String name;
        private String platform;
        private String status;

        @JsonProperty("subscription_type")
        private String subscriptionType;

        @JsonProperty("rate_multiplier")
        private double rateMultiplier;

        @JsonProperty("daily_limit_usd")
        private Double dailyLimitUsd;

        @JsonProperty("weekly_limit_usd")
        private Double weeklyLimitUsd;

        @JsonProperty("monthly_limit_usd")
        private Double monthlyLimitUsd;

        @JsonProperty("image_price_1k")
        private Double imagePrice1k;

        @JsonProperty("image_price_2k")
        private Double imagePrice2k;

        @JsonProperty("image_price_4k")
        private Double imagePrice4k;

        @JsonProperty("claude_code_only")
        private boolean claudeCodeOnly;

        @JsonProperty("fallback_group_id")
        private Long fallbackGroupId;

        @JsonProperty("fallback_group_id_on_invalid_request")
        private Long fallbackGroupIdOnInvalidRequest;

        @JsonProperty("model_routing")
        private Map<String, List<Long>> modelRouting;

        @JsonProperty("model_routing_enabled")
        private boolean modelRoutingEnabled;

        @JsonProperty("mcp_xml_inject")
        private boolean mcpXmlInject;

        @JsonProperty("supported_model_scopes")
        private List<String> supportedModelScopes;

        @JsonProperty("allow_messages_dispatch")
        private boolean allowMessagesDispatch;

        @JsonProperty("default_mapped_model")
        private String defaultMappedModel;
    }

    /**
     * 缓存条目（支持负缓存）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CacheEntry {
        private boolean notFound;
        private APIKeyAuthSnapshot snapshot;
    }

    /**
     * 获取 API Key 认证快照
     *
     * @param apiKeyHash API Key 的哈希值
     * @return 认证快照，不存在返回 null
     */
    public APIKeyAuthSnapshot getAuthSnapshot(String apiKeyHash) {
        try {
            String key = API_KEY_AUTH_CACHE_PREFIX + apiKeyHash;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }

            CacheEntry entry = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, CacheEntry.class);

            // 如果是负缓存（不存在），返回 null
            if (entry.isNotFound()) {
                return null;
            }

            return entry.getSnapshot();
        } catch (Exception e) {
            log.warn("Failed to get API key auth snapshot from cache: hash={}, error={}", apiKeyHash, e.getMessage());
            return null;
        }
    }

    /**
     * 设置 API Key 认证快照
     *
     * @param apiKeyHash API Key 的哈希值
     * @param snapshot   认证快照
     */
    public void setAuthSnapshot(String apiKeyHash, APIKeyAuthSnapshot snapshot) {
        try {
            String key = API_KEY_AUTH_CACHE_PREFIX + apiKeyHash;
            CacheEntry entry = new CacheEntry();
            entry.setSnapshot(snapshot);
            entry.setNotFound(false);

            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json, API_KEY_AUTH_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to set API key auth snapshot to cache: hash={}, error={}", apiKeyHash, e.getMessage());
        }
    }

    /**
     * 设置 API Key 不存在（负缓存）
     *
     * @param apiKeyHash API Key 的哈希值
     */
    public void setNotFound(String apiKeyHash) {
        try {
            String key = API_KEY_AUTH_CACHE_PREFIX + apiKeyHash;
            CacheEntry entry = new CacheEntry();
            entry.setNotFound(true);
            entry.setSnapshot(null);

            // 负缓存时间短一些，避免缓存太久导致新注册的 key 无法使用
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json, 60, TimeUnit.SECONDS); // 1 minute for negative cache
        } catch (Exception e) {
            log.warn("Failed to set API key not found to cache: hash={}, error={}", apiKeyHash, e.getMessage());
        }
    }

    /**
     * 使 API Key 认证缓存失效
     *
     * @param apiKeyHash API Key 的哈希值
     */
    public void invalidate(String apiKeyHash) {
        try {
            String key = API_KEY_AUTH_CACHE_PREFIX + apiKeyHash;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate API key auth cache: hash={}, error={}", apiKeyHash, e.getMessage());
        }
    }

    /**
     * 使指定用户的所有 API Key 缓存失效
     *
     * @param userId 用户 ID
     */
    public void invalidateByUserId(Long userId) {
        try {
            // 使用模式匹配删除该用户的所有 API Key 缓存
            String pattern = API_KEY_AUTH_CACHE_PREFIX + "*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    try {
                        String json = redisTemplate.opsForValue().get(key);
                        if (json != null) {
                            CacheEntry entry = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(json, CacheEntry.class);
                            if (entry.getSnapshot() != null && userId.equals(entry.getSnapshot().getUserId())) {
                                redisTemplate.delete(key);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate API key auth cache by userId: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 检查 API Key 是否过期
     */
    public boolean isExpired(APIKeyAuthSnapshot snapshot) {
        if (snapshot == null || snapshot.getExpiresAt() == null) {
            return false;
        }
        try {
            Instant expiresAt = Instant.parse(snapshot.getExpiresAt());
            return Instant.now().isAfter(expiresAt);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查配额是否充足
     */
    public boolean hasQuota(APIKeyAuthSnapshot snapshot, double cost) {
        if (snapshot == null || snapshot.getQuota() <= 0) {
            return true; // 无配额限制
        }
        return snapshot.getQuotaUsed() + cost <= snapshot.getQuota();
    }
}
