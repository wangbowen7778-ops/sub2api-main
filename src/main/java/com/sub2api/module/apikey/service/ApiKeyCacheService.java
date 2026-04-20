package com.sub2api.module.apikey.service;

import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * API Key cache service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class ApiKeyCacheService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCacheService.class);

    private static final String API_KEY_CACHE_PREFIX = "apikey:";
    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Cache API Key info
     */
    public void cacheApiKeyInfo(String rawKey, ApiKeyInfo info) {
        try {
            String cacheKey = API_KEY_CACHE_PREFIX + rawKey.substring(0, 20);
            stringRedisTemplate.opsForValue().set(cacheKey, toJson(info), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache API Key info: {}", e.getMessage());
        }
    }

    /**
     * Get cached API Key info
     */
    public ApiKeyInfo getApiKeyInfo(String rawKey) {
        try {
            String cacheKey = API_KEY_CACHE_PREFIX + rawKey.substring(0, 20);
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return fromJson(json);
            }
        } catch (Exception e) {
            log.warn("Failed to get API Key cache info: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Delete cache
     */
    public void deleteCache(String rawKey) {
        try {
            String cacheKey = API_KEY_CACHE_PREFIX + rawKey.substring(0, 20);
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to delete API Key cache: {}", e.getMessage());
        }
    }

    private String toJson(ApiKeyInfo info) {
        return String.format(
                "{\"keyId\":%d,\"userId\":%d,\"keyPrefix\":\"%s\",\"groupIds\":\"%s\",\"scope\":\"%s\",\"status\":\"%s\",\"expireAt\":\"%s\",\"rateLimit\":%d}",
                info.getKeyId(), info.getUserId(), info.getKeyPrefix(),
                info.getGroupIds() != null ? info.getGroupIds() : "",
                info.getScope() != null ? info.getScope() : "",
                info.getStatus() != null ? info.getStatus() : "",
                info.getExpireAt() != null ? info.getExpireAt().toString() : "",
                info.getRateLimit() != null ? info.getRateLimit() : 0
        );
    }

    private ApiKeyInfo fromJson(String json) {
        try {
            // Simple JSON parsing
            ApiKeyInfo info = new ApiKeyInfo();
            json = json.substring(1, json.length() - 1); // Remove {}
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length != 2) continue;
                String key = kv[0].replace("\"", "").trim();
                String value = kv[1].replace("\"", "").trim();

                switch (key) {
                    case "keyId" -> info.setKeyId(Long.parseLong(value));
                    case "userId" -> info.setUserId(Long.parseLong(value));
                    case "keyPrefix" -> info.setKeyPrefix(value);
                    case "groupIds" -> info.setGroupIds(value.isEmpty() ? null : value);
                    case "scope" -> info.setScope(value);
                    case "status" -> info.setStatus(value);
                    case "expireAt" -> info.setExpireAt(value.isEmpty() ? null : java.time.OffsetDateTime.parse(value));
                    case "rateLimit" -> info.setRateLimit(Integer.parseInt(value));
                }
            }
            return info;
        } catch (Exception e) {
            log.warn("Failed to parse API Key JSON: {}", e.getMessage());
            return null;
        }
    }
}
