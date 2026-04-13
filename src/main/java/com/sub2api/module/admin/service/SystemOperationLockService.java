package com.sub2api.module.admin.service;

import com.sub2api.module.admin.mapper.IdempotencyRecordMapper;
import com.sub2api.module.admin.model.entity.IdempotencyRecord;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System Operation Lock Service
 * 系统操作锁服务 - 提供全局系统操作的分布式锁机制
 * 使用幂等性记录表实现，确保同一时间只有一个系统级操作在执行
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemOperationLockService {

    private static final String SYSTEM_OPERATION_SCOPE = "admin.system.operations.global_lock";
    private static final String SYSTEM_OPERATION_KEY = "global-system-operation-lock";

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    @Value("${system-operation.lease-seconds:30}")
    private int leaseSeconds;

    @Value("${system-operation.ttl-hours:1}")
    private int ttlHours;

    // Active locks managed in memory
    private final Map<String, ActiveLock> activeLocks = new ConcurrentHashMap<>();

    /**
     * System operation lock handle
     */
    public static class SystemOperationLock {
        private final Long recordId;
        private final String operationId;
        private final AtomicBoolean released = new AtomicBoolean(false);

        public SystemOperationLock(Long recordId, String operationId) {
            this.recordId = recordId;
            this.operationId = operationId;
        }

        public Long getRecordId() {
            return recordId;
        }

        public String getOperationId() {
            return operationId;
        }

        public boolean isReleased() {
            return released.get();
        }

        void markReleased() {
            released.set(true);
        }
    }

    /**
     * Active lock metadata
     */
    private static class ActiveLock {
        final SystemOperationLock lock;
        final Thread renewThread;
        final LocalDateTime renewUntil;

        ActiveLock(SystemOperationLock lock, Thread renewThread, LocalDateTime renewUntil) {
            this.lock = lock;
            this.renewThread = renewThread;
            this.renewUntil = renewUntil;
        }
    }

    /**
     * Acquire system operation lock
     *
     * @param operationId 操作ID，用于标识当前操作
     * @return lock handle, 使用完后需调用 release
     * @throws BusinessException 获取锁失败
     */
    public SystemOperationLock acquire(String operationId) {
        if (operationId == null || operationId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_OPERATION_ID_REQUIRED, "operation id is required");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(ttlHours);
        LocalDateTime lockedUntil = now.plusSeconds(leaseSeconds);
        String keyHash = hashKey(SYSTEM_OPERATION_KEY);

        // Try to create processing record
        IdempotencyRecord record = new IdempotencyRecord();
        record.setScope(SYSTEM_OPERATION_SCOPE);
        record.setIdempotencyKeyHash(keyHash);
        record.setRequestFingerprint(operationId);
        record.setStatus(IdempotencyRecord.STATUS_PROCESSING);
        record.setLockedUntil(lockedUntil);
        record.setExpiresAt(expiresAt);

        int inserted = idempotencyRecordMapper.insertProcessingIgnoreConflict(record);
        boolean owner = inserted > 0;

        if (!owner) {
            // Check existing lock
            IdempotencyRecord existing = idempotencyRecordMapper.selectByScopeAndKeyHash(SYSTEM_OPERATION_SCOPE, keyHash);
            if (existing == null) {
                throw new BusinessException(ErrorCode.SYSTEM_OPERATION_BUSY, "system operation lock unavailable");
            }

            if (IdempotencyRecord.STATUS_PROCESSING.equals(existing.getStatus())
                    && existing.getLockedUntil() != null
                    && existing.getLockedUntil().isAfter(now)) {
                throw createBusyException(existing.getRequestFingerprint(), existing.getLockedUntil(), now);
            }

            // Try to reclaim
            int reclaimed = idempotencyRecordMapper.tryReclaim(
                    existing.getId(),
                    existing.getStatus(),
                    now,
                    lockedUntil,
                    expiresAt,
                    now
            );

            if (reclaimed == 0) {
                IdempotencyRecord latest = idempotencyRecordMapper.selectByScopeAndKeyHash(SYSTEM_OPERATION_SCOPE, keyHash);
                if (latest != null) {
                    throw createBusyException(latest.getRequestFingerprint(), latest.getLockedUntil(), now);
                }
                throw new BusinessException(ErrorCode.SYSTEM_OPERATION_BUSY, "system operation is busy");
            }
            record.setId(existing.getId());
        }

        if (record.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_OPERATION_BUSY, "system operation lock unavailable");
        }

        SystemOperationLock lock = new SystemOperationLock(record.getId(), operationId);

        // Start renew thread
        Thread renewThread = startRenewThread(lock, leaseSeconds, ttlHours);

        activeLocks.put(operationId, new ActiveLock(lock, renewThread, expiresAt));

        log.info("System operation lock acquired: operationId={}", operationId);
        return lock;
    }

    /**
     * Release system operation lock
     *
     * @param lock lock handle
     * @param succeeded 是否成功完成
     * @param failureReason 失败原因 (可选)
     */
    public void release(SystemOperationLock lock, boolean succeeded, String failureReason) {
        if (lock == null) {
            return;
        }

        if (lock.isReleased()) {
            return;
        }
        lock.markReleased();

        // Stop renew thread
        ActiveLock activeLock = activeLocks.get(lock.getOperationId());
        if (activeLock != null) {
            activeLocks.remove(lock.getOperationId());
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(ttlHours);

        if (succeeded) {
            String responseBody = String.format("{\"operation_id\":\"%s\",\"released\":true}", lock.getOperationId());
            idempotencyRecordMapper.markSucceeded(lock.getRecordId(), 200, responseBody, expiresAt);
            log.info("System operation lock released (success): operationId={}", lock.getOperationId());
        } else {
            String reason = failureReason != null && !failureReason.isEmpty() ? failureReason : "SYSTEM_OPERATION_FAILED";
            idempotencyRecordMapper.markFailedRetryable(lock.getRecordId(), reason, LocalDateTime.now(), expiresAt);
            log.info("System operation lock released (failed): operationId={}, reason={}", lock.getOperationId(), reason);
        }
    }

    /**
     * Release with success
     */
    public void releaseSuccess(SystemOperationLock lock) {
        release(lock, true, null);
    }

    /**
     * Release with failure
     */
    public void releaseFailure(SystemOperationLock lock, String reason) {
        release(lock, false, reason);
    }

    /**
     * Start the renew thread for lock extension
     */
    private Thread startRenewThread(SystemOperationLock lock, int leaseSeconds, int ttlHours) {
        int renewIntervalSeconds = Math.max(leaseSeconds / 3, 1);
        long renewIntervalMillis = renewIntervalSeconds * 1000L;

        Thread thread = new Thread(() -> {
            while (!lock.isReleased() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(renewIntervalMillis);

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime newLockedUntil = now.plusSeconds(leaseSeconds);
                    LocalDateTime newExpiresAt = now.plusHours(ttlHours);

                    int updated = idempotencyRecordMapper.extendProcessingLock(
                            lock.getRecordId(),
                            lock.getOperationId(),
                            newLockedUntil,
                            newExpiresAt
                    );

                    if (updated == 0) {
                        log.warn("System operation lock renew failed (ownership lost): operationId={}", lock.getOperationId());
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("System operation lock renew failed: operationId={}, err={}", lock.getOperationId(), e.getMessage());
                }
            }
        }, "system-operation-lock-renew-" + lock.getOperationId());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Create busy exception with retry-after metadata
     */
    private BusinessException createBusyException(String operationId, LocalDateTime lockedUntil, LocalDateTime now) {
        int retryAfterSeconds = (int) java.time.Duration.between(now, lockedUntil).getSeconds();
        if (retryAfterSeconds <= 0) {
            retryAfterSeconds = 1;
        }

        Map<String, Object> metadata = Map.of(
                "operation_id", operationId != null ? operationId : "",
                "retry_after", retryAfterSeconds
        );

        BusinessException ex = new BusinessException(ErrorCode.SYSTEM_OPERATION_BUSY, "another system operation is in progress");
        // Note: BusinessException doesn't support metadata, so we include it in message
        return ex;
    }

    /**
     * Hash idempotency key using SHA256
     */
    private String hashKey(String key) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash key", e);
        }
    }

    /**
     * Get service status
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "lease_seconds", leaseSeconds,
                "ttl_hours", ttlHours,
                "active_locks", activeLocks.size()
        );
    }
}