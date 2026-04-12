package com.sub2api.module.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的身份缓存
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIdentityCache implements IdentityService.IdentityCache {

    private static final String FINGERPRINT_CACHE_PREFIX = "identity:fingerprint:";
    private static final String MASKED_SESSION_CACHE_PREFIX = "identity:masked_session:";
    private static final long FINGERPRINT_TTL_DAYS = 7;
    private static final long MASKED_SESSION_TTL_MINUTES = 15;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IdentityService.Fingerprint getFingerprint(long accountId) {
        try {
            String key = FINGERPRINT_CACHE_PREFIX + accountId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, IdentityService.Fingerprint.class);
        } catch (Exception e) {
            log.warn("Failed to get fingerprint from cache: accountId={}, error={}", accountId, e.getMessage());
            return null;
        }
    }

    @Override
    public void setFingerprint(long accountId, IdentityService.Fingerprint fingerprint) {
        try {
            String key = FINGERPRINT_CACHE_PREFIX + accountId;
            String json = objectMapper.writeValueAsString(fingerprint);
            redisTemplate.opsForValue().set(key, json, FINGERPRINT_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to set fingerprint to cache: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    @Override
    public String getMaskedSessionId(long accountId) {
        try {
            String key = MASKED_SESSION_CACHE_PREFIX + accountId;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to get masked session ID from cache: accountId={}, error={}", accountId, e.getMessage());
            return null;
        }
    }

    @Override
    public void setMaskedSessionId(long accountId, String sessionId) {
        try {
            String key = MASKED_SESSION_CACHE_PREFIX + accountId;
            redisTemplate.opsForValue().set(key, sessionId, MASKED_SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to set masked session ID to cache: accountId={}, error={}", accountId, e.getMessage());
        }
    }
}
