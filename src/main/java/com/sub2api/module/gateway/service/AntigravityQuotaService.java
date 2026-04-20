package com.sub2api.module.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Antigravity Quota 服务
 * 从 Antigravity API 获取账号额度信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntigravityQuotaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String ANTI_GRAVITY_API_BASE = "https://api.antigravity.dev";

    // 缓存 key 前缀
    private static final String QUOTA_CACHE_PREFIX = "antigravity:quota:";
    private static final String USAGE_INFO_CACHE_PREFIX = "antigravity:usage:";

    // 缓存时间
    private static final Duration QUOTA_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Quota 结果
     */
    @Data
    public static class QuotaResult {
        private UsageInfo usageInfo;
        private String raw;
    }

    /**
     * 用量信息
     */
    @Data
    public static class UsageInfo {
        private OffsetDateTime updatedAt;
        private boolean forbidden;
        private String forbiddenReason;
        private String forbiddenType;
        private String validationUrl;
        private boolean needsVerify;
        private boolean isBanned;
        private String errorCode;
        private Map<String, ModelQuota> antigravityQuota;
        private Map<String, ModelDetail> antigravityQuotaDetails;
        private String subscriptionTier;
        private String subscriptionTierRaw;
        private Double creditsBalance;
        private Double creditsUsed;
    }

    /**
     * 模型额度
     */
    @Data
    public static class ModelQuota {
        private int utilization; // 0-100
        private Long resetTime;
        private Long remaining;
        private Long total;
    }

    /**
     * 模型详情
     */
    @Data
    public static class ModelDetail {
        private String modelName;
        private Long limit;
        private Long remaining;
        private String resetTime;
        private String promptTokens;
        private String completionTokens;
    }

    /**
     * 额度获取结果
     */
    @Data
    public static class FetchResult {
        private boolean success;
        private String error;
        private UsageInfo usageInfo;
    }

    /**
     * 检查是否可以获取此账户的额度
     */
    public boolean canFetch(Account account) {
        if (account == null || !"antigravity".equalsIgnoreCase(account.getPlatform())) {
            return false;
        }

        Map<String, Object> credentials = account.getCredentials();
        if (credentials == null) {
            return false;
        }

        Object accessToken = credentials.get("access_token");
        return accessToken != null && !accessToken.toString().isBlank();
    }

    /**
     * 获取 Antigravity 账户额度信息
     */
    public QuotaResult fetchQuota(Account account) {
        return fetchQuota(account, "");
    }

    /**
     * 获取 Antigravity 账户额度信息
     */
    public QuotaResult fetchQuota(Account account, String proxyURL) {
        Map<String, Object> credentials = account.getCredentials();
        if (credentials == null) {
            return null;
        }

        String accessToken = "";
        String projectId = "";

        Object tokenObj = credentials.get("access_token");
        if (tokenObj != null) {
            accessToken = tokenObj.toString();
        }

        Object projectIdObj = credentials.get("project_id");
        if (projectIdObj != null) {
            projectId = projectIdObj.toString();
        }

        if (accessToken.isBlank()) {
            log.warn("No access token for account {}", account.getId());
            return null;
        }

        try {
            // 获取可用模型列表
            ModelsResponse modelsResp = fetchAvailableModels(accessToken, projectId, proxyURL);

            // 获取订阅等级和 credits 余额（非关键路径）
            SubscriptionInfo subscriptionInfo = fetchSubscriptionInfo(accessToken, proxyURL);

            // 构建 UsageInfo
            UsageInfo usageInfo = buildUsageInfo(modelsResp, subscriptionInfo);

            QuotaResult result = new QuotaResult();
            result.setUsageInfo(usageInfo);
            result.setRaw(objectMapper.writeValueAsString(modelsResp));

            // 缓存结果
            cacheQuota(account.getId(), result);

            return result;

        } catch (Exception e) {
            log.error("Failed to fetch quota for account {}: {}", account.getId(), e.getMessage());
            throw new RuntimeException("Failed to fetch quota: " + e.getMessage(), e);
        }
    }

    /**
     * 获取模型额度（带缓存）
     */
    public QuotaResult getQuotaWithCache(Account account) {
        // 先检查缓存
        QuotaResult cached = getCachedQuota(account.getId());
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，获取新数据
        return fetchQuota(account);
    }

    /**
     * 获取用量信息
     */
    public UsageInfo getUsageInfo(Account account) {
        QuotaResult result = getQuotaWithCache(account);
        return result != null ? result.getUsageInfo() : null;
    }

    // ========== 私有方法 ==========

    private ModelsResponse fetchAvailableModels(String accessToken, String projectId, String proxyURL) {
        String baseURL = proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL;
        String url = baseURL + "/v1/models";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        if (!projectId.isBlank()) {
            headers.set("x-goog-user-project", projectId);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            ModelsResponse result = new ModelsResponse();
            result.setRoot(root);

            Map<String, ModelQuota> quotaMap = new HashMap<>();
            Map<String, ModelDetail> detailMap = new HashMap<>();

            if (root.has("models")) {
                JsonNode models = root.get("models");
                models.fields().forEachRemaining(entry -> {
                    String modelName = entry.getKey();
                    JsonNode modelInfo = entry.getValue();

                    ModelQuota quota = new ModelQuota();
                    ModelDetail detail = new ModelDetail();

                    if (modelInfo.has("quota_info")) {
                        JsonNode quotaInfo = modelInfo.get("quota_info");

                        // remaining_fraction 是剩余比例 (0.0-1.0)
                        double remainingFraction = quotaInfo.has("remaining_fraction")
                                ? quotaInfo.get("remaining_fraction").asDouble() : 1.0;
                        quota.setUtilization((int) ((1.0 - remainingFraction) * 100));

                        if (quotaInfo.has("reset_time")) {
                            quota.setResetTime(quotaInfo.get("reset_time").asLong());
                        }

                        detail.setLimit(quotaInfo.has("limit") ? quotaInfo.get("limit").asLong() : 0L);
                        detail.setRemaining(quotaInfo.has("remaining") ? quotaInfo.get("remaining").asLong() : 0L);
                        if (quotaInfo.has("reset_time")) {
                            detail.setResetTime(String.valueOf(quotaInfo.get("reset_time").asLong()));
                        }
                    }

                    quotaMap.put(modelName, quota);
                    detail.setModelName(modelName);
                    detailMap.put(modelName, detail);
                });
            }

            result.setQuotaMap(quotaMap);
            result.setDetailMap(detailMap);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse models response: " + e.getMessage(), e);
        }
    }

    private SubscriptionInfo fetchSubscriptionInfo(String accessToken, String proxyURL) {
        String baseURL = proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL;
        String url = baseURL + "/v1/codeassist/load";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("mode", "subscription");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            SubscriptionInfo info = new SubscriptionInfo();

            if (root.has("current_tier")) {
                info.setCurrentTier(root.get("current_tier").asText());
            }

            if (root.has("ai_credits")) {
                JsonNode credits = root.get("ai_credits");
                if (credits.has("balance")) {
                    info.setCreditsBalance(credits.get("balance").asDouble());
                }
                if (credits.has("used")) {
                    info.setCreditsUsed(credits.get("used").asDouble());
                }
            }

            if (root.has("ineligible_tiers") && root.get("ineligible_tiers").isArray()) {
                info.setHasIneligibleTiers(root.get("ineligible_tiers").size() > 0);
                if (info.isHasIneligibleTiers() && root.get("ineligible_tiers").size() > 0) {
                    JsonNode firstTier = root.get("ineligible_tiers").get(0);
                    if (firstTier.has("reason_message")) {
                        info.setIneligibleReason(firstTier.get("reason_message").asText());
                    }
                }
            }

            return info;

        } catch (Exception e) {
            log.warn("Failed to fetch subscription info: {}", e.getMessage());
            return new SubscriptionInfo();
        }
    }

    private UsageInfo buildUsageInfo(ModelsResponse modelsResp, SubscriptionInfo subscriptionInfo) {
        UsageInfo info = new UsageInfo();
        info.setUpdatedAt(OffsetDateTime.now());
        info.setAntigravityQuota(modelsResp.getQuotaMap());
        info.setAntigravityQuotaDetails(modelsResp.getDetailMap());

        // 设置订阅等级
        String tier = subscriptionInfo.getCurrentTier();
        if (tier != null && !tier.isBlank()) {
            info.setSubscriptionTierRaw(tier);
            info.setSubscriptionTier(normalizeTier(tier));
        }

        // 设置 credits
        if (subscriptionInfo.getCreditsBalance() != null) {
            info.setCreditsBalance(subscriptionInfo.getCreditsBalance());
        }
        if (subscriptionInfo.getCreditsUsed() != null) {
            info.setCreditsUsed(subscriptionInfo.getCreditsUsed());
        }

        // 处理异常状态
        if (subscriptionInfo.isHasIneligibleTiers()) {
            info.setForbidden(true);
            info.setForbiddenType("abnormal");
            info.setForbiddenReason(subscriptionInfo.getIneligibleReason());
            info.setNeedsVerify(true);
        }

        return info;
    }

    private String normalizeTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String lower = raw.toLowerCase();
        if (lower.contains("ultra")) {
            return "ULTRA";
        }
        if (lower.contains("pro")) {
            return "PRO";
        }
        if (lower.contains("free")) {
            return "FREE";
        }
        return "UNKNOWN";
    }

    // ========== 缓存方法 ==========

    private void cacheQuota(Long accountId, QuotaResult result) {
        String key = QUOTA_CACHE_PREFIX + accountId;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, QUOTA_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache quota for account {}: {}", accountId, e.getMessage());
        }
    }

    private QuotaResult getCachedQuota(Long accountId) {
        String key = QUOTA_CACHE_PREFIX + accountId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, QuotaResult.class);
            }
        } catch (Exception e) {
            log.warn("Failed to get cached quota for account {}: {}", accountId, e.getMessage());
        }
        return null;
    }

    /**
     * 使缓存失效
     */
    public void invalidateCache(Long accountId) {
        String key = QUOTA_CACHE_PREFIX + accountId;
        redisTemplate.delete(key);
    }

    // ========== 内部类 ==========

    @Data
    private static class ModelsResponse {
        private JsonNode root;
        private Map<String, ModelQuota> quotaMap;
        private Map<String, ModelDetail> detailMap;
    }

    @Data
    private static class SubscriptionInfo {
        private String currentTier;
        private Double creditsBalance;
        private Double creditsUsed;
        private boolean hasIneligibleTiers;
        private String ineligibleReason;
    }
}