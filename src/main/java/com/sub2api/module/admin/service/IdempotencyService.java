package com.sub2api.module.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.admin.mapper.IdempotencyRecordMapper;
import com.sub2api.module.admin.model.entity.IdempotencyRecord;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性服务
 * 用于确保重复请求的幂等性处理
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;

    // 默认配置
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FAILED_RETRY_BACKOFF = Duration.ofSeconds(5);
    private static final int MAX_STORED_RESPONSE_LEN = 64 * 1024;

    // 幂等性错误码
    public static final String ERR_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String ERR_KEY_INVALID = "IDEMPOTENCY_KEY_INVALID";
    public static final String ERR_KEY_CONFLICT = "IDEMPOTENCY_KEY_CONFLICT";
    public static final String ERR_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    public static final String ERR_RETRY_BACKOFF = "IDEMPOTENCY_RETRY_BACKOFF";
    public static final String ERR_STORE_UNAVAIL = "IDEMPOTENCY_STORE_UNAVAILABLE";
    public static final String ERR_INVALID_PAYLOAD = "IDEMPOTENCY_PAYLOAD_INVALID";

    /**
     * 验证幂等性键格式
     *
     * @param key 幂等性键
     * @return 标准化后的键
     */
    public String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 128) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Idempotency key too long (max 128 chars)");
        }
        // 检查有效字符 (33-126 ASCII)
        for (char c : trimmed.toCharArray()) {
            if (c < 33 || c > 126) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Idempotency key contains invalid characters");
            }
        }
        return trimmed;
    }

    /**
     * 计算幂等性键的哈希值
     *
     * @param key 幂等性键
     * @return SHA-256 哈希值 (Base64 编码)
     */
    public String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 计算请求体的指纹
     *
     * @param payload 请求体
     * @return 请求体指纹
     */
    public String computeFingerprint(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        } catch (Exception e) {
            log.warn("Failed to compute fingerprint: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 执行幂等性请求
     *
     * @param scope 作用域
     * @param actorScope 行为者作用域 (如 "user:123")
     * @param method HTTP 方法
     * @param route 路由
     * @param idempotencyKey 幂等性键
     * @param payload 请求体
     * @param ttl TTL
     * @param requireKey 是否必须提供幂等性键
     * @param execute 执行函数
     * @return 幂等性执行结果
     */
    @Transactional
    public IdempotencyExecuteResult execute(
            String scope,
            String actorScope,
            String method,
            String route,
            String idempotencyKey,
            Object payload,
            Duration ttl,
            boolean requireKey,
            java.util.function.Supplier<Object> execute) {

        // 验证幂等性键
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            if (requireKey) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Idempotency key is required");
            }
            // 不需要键，直接执行
            return new IdempotencyExecuteResult(execute.get(), false);
        }

        // 标准化和验证键
        String normalizedKey = normalizeKey(idempotencyKey);
        if (normalizedKey == null || normalizedKey.isEmpty()) {
            if (requireKey) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Idempotency key is required");
            }
            return new IdempotencyExecuteResult(execute.get(), false);
        }

        String keyHash = hashKey(normalizedKey);
        String fingerprint = computeFingerprint(payload);
        Duration effectiveTtl = ttl != null ? ttl : DEFAULT_TTL;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(effectiveTtl);

        // 尝试创建处理中记录
        IdempotencyRecord newRecord = new IdempotencyRecord();
        newRecord.setScope(scope);
        newRecord.setIdempotencyKeyHash(keyHash);
        newRecord.setRequestFingerprint(fingerprint);
        newRecord.setStatus(IdempotencyRecord.STATUS_PROCESSING);
        newRecord.setLockedUntil(now.plus(PROCESSING_TIMEOUT));
        newRecord.setExpiresAt(expiresAt);

        try {
            int inserted = idempotencyRecordMapper.insertProcessingIgnoreConflict(newRecord);

            if (inserted > 0) {
                // 成功插入，我们是处理者
                return processWithLock(scope, actorScope, method, route, keyHash, fingerprint,
                        normalizedKey, newRecord.getId(), expiresAt, execute);
            } else {
                // 记录已存在，检查状态
                return handleExistingRecord(scope, actorScope, method, route, keyHash, fingerprint,
                        normalizedKey, expiresAt, execute);
            }
        } catch (Exception e) {
            log.error("Idempotency operation failed: scope={}, key={}, error={}", scope, normalizedKey, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Idempotency operation failed");
        }
    }

    /**
     * 处理已存在的记录
     */
    private IdempotencyExecuteResult handleExistingRecord(
            String scope,
            String actorScope,
            String method,
            String route,
            String keyHash,
            String fingerprint,
            String normalizedKey,
            LocalDateTime expiresAt,
            java.util.function.Supplier<Object> execute) {

        IdempotencyRecord existing = idempotencyRecordMapper.selectByScopeAndKeyHash(scope, keyHash);

        if (existing == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Idempotency record not found");
        }

        // 检查是否过期
        if (existing.isExpired()) {
            // 记录已过期，尝试重新获取锁
            LocalDateTime now = LocalDateTime.now();
            int updated = idempotencyRecordMapper.tryReclaim(
                    existing.getId(),
                    IdempotencyRecord.STATUS_PROCESSING,
                    now.plus(PROCESSING_TIMEOUT),
                    expiresAt,
                    now);

            if (updated > 0) {
                return processWithLock(scope, actorScope, method, route, keyHash, fingerprint,
                        normalizedKey, existing.getId(), expiresAt, execute);
            }
            // 竞争失败，重新查询
            return handleExistingRecord(scope, actorScope, method, route, keyHash, fingerprint,
                    normalizedKey, expiresAt, execute);
        }

        // 检查请求指纹是否匹配
        if (!fingerprint.equals(existing.getRequestFingerprint()) &&
                !existing.getRequestFingerprint().isEmpty() &&
                !fingerprint.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Idempotency key reused with different payload");
        }

        // 根据状态处理
        switch (existing.getStatus()) {
            case IdempotencyRecord.STATUS_PROCESSING:
                // 正在处理中
                if (existing.isLocked()) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Idempotency request is still processing");
                }
                // 锁已释放，尝试重新获取
                int updated = idempotencyRecordMapper.tryReclaim(
                        existing.getId(),
                        IdempotencyRecord.STATUS_PROCESSING,
                        LocalDateTime.now().plus(PROCESSING_TIMEOUT),
                        expiresAt,
                        LocalDateTime.now());

                if (updated > 0) {
                    return processWithLock(scope, actorScope, method, route, keyHash, fingerprint,
                            normalizedKey, existing.getId(), expiresAt, execute);
                }
                // 竞争失败
                throw new BusinessException(ErrorCode.CONFLICT, "Idempotency request is still processing");

            case IdempotencyRecord.STATUS_SUCCEEDED:
                // 成功，直接返回缓存结果
                return replayResult(existing);

            case IdempotencyRecord.STATUS_FAILED_RETRYABLE:
                // 可重试失败，检查退避窗口
                if (existing.getLockedUntil() != null &&
                        existing.getLockedUntil().isAfter(LocalDateTime.now())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Idempotency request is in retry backoff window");
                }
                // 尝试重新获取锁
                int updated = idempotencyRecordMapper.tryReclaim(
                        existing.getId(),
                        IdempotencyRecord.STATUS_FAILED_RETRYABLE,
                        LocalDateTime.now().plus(PROCESSING_TIMEOUT),
                        expiresAt,
                        LocalDateTime.now());

                if (updated > 0) {
                    return processWithLock(scope, actorScope, method, route, keyHash, fingerprint,
                            normalizedKey, existing.getId(), expiresAt, execute);
                }
                throw new BusinessException(ErrorCode.CONFLICT, "Idempotency request is still processing");

            default:
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unknown idempotency status");
        }
    }

    /**
     * 使用锁处理请求
     */
    private IdempotencyExecuteResult processWithLock(
            String scope,
            String actorScope,
            String method,
            String route,
            String keyHash,
            String fingerprint,
            String normalizedKey,
            Long recordId,
            LocalDateTime expiresAt,
            java.util.function.Supplier<Object> execute) {

        try {
            // 执行实际请求
            Object result = execute.get();

            // 序列化结果
            String responseBody;
            try {
                responseBody = objectMapper.writeValueAsString(result);
                if (responseBody.length() > MAX_STORED_RESPONSE_LEN) {
                    responseBody = responseBody.substring(0, MAX_STORED_RESPONSE_LEN);
                }
            } catch (Exception e) {
                log.warn("Failed to serialize idempotency response: {}", e.getMessage());
                responseBody = "\"error\": \"Failed to serialize response\"";
            }

            // 标记成功
            idempotencyRecordMapper.markSucceeded(recordId, 200, responseBody, expiresAt);

            return new IdempotencyExecuteResult(result, false);

        } catch (BusinessException e) {
            // 业务异常，标记为可重试失败
            LocalDateTime retryUntil = LocalDateTime.now().plus(FAILED_RETRY_BACKOFF);
            idempotencyRecordMapper.markFailedRetryable(
                    recordId,
                    e.getMessage(),
                    retryUntil,
                    expiresAt);
            throw e;

        } catch (Exception e) {
            // 其他异常，标记为可重试失败
            LocalDateTime retryUntil = LocalDateTime.now().plus(FAILED_RETRY_BACKOFF);
            idempotencyRecordMapper.markFailedRetryable(
                    recordId,
                    e.getMessage(),
                    retryUntil,
                    expiresAt);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Request failed: " + e.getMessage());
        }
    }

    /**
     * 重放缓存的结果
     */
    private IdempotencyExecuteResult replayResult(IdempotencyRecord record) {
        if (record.getResponseBody() == null || record.getResponseBody().isEmpty()) {
            return new IdempotencyExecuteResult(null, true);
        }

        try {
            Object data = objectMapper.readValue(record.getResponseBody(), Object.class);
            return new IdempotencyExecuteResult(data, true);
        } catch (Exception e) {
            log.error("Failed to replay idempotency result: {}", e.getMessage());
            // 重放失败，返回原始结果
            return new IdempotencyExecuteResult(null, true);
        }
    }

    /**
     * 获取有效的幂等性键
     */
    public Optional<String> getValidKey(String rawKey, boolean required) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            if (required) {
                return Optional.empty();
            }
            return Optional.of(java.util.UUID.randomUUID().toString());
        }

        try {
            String normalized = normalizeKey(rawKey.trim());
            return Optional.of(normalized);
        } catch (BusinessException e) {
            if (required) {
                return Optional.empty();
            }
            return Optional.of(java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * 定时清理过期记录
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void cleanupExpired() {
        try {
            int deleted = idempotencyRecordMapper.deleteExpired(LocalDateTime.now(), 1000);
            if (deleted > 0) {
                log.info("Cleaned up {} expired idempotency records", deleted);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired idempotency records: {}", e.getMessage());
        }
    }

    /**
     * 幂等性执行结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class IdempotencyExecuteResult {
        private Object data;
        private boolean replayed;  // 是否是重放的缓存结果
    }
}
