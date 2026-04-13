package com.sub2api.module.admin.service;

import com.sub2api.module.admin.mapper.IdempotencyRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Idempotency Cleanup Service
 * 幂等性记录清理服务 - 定期清理已过期的幂等记录，避免表无限增长
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    @Value("${idempotency.cleanup-interval-seconds:60}")
    private int cleanupIntervalSeconds;

    @Value("${idempotency.cleanup-batch-size:500}")
    private int cleanupBatchSize;

    /**
     * 启动时先清理一轮，防止重启后积压
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 60000)
    public void runOnce() {
        try {
            int deleted = idempotencyRecordMapper.deleteExpired(
                    java.time.LocalDateTime.now(),
                    cleanupBatchSize);

            if (deleted > 0) {
                log.info("IdempotencyCleanup: cleaned {} expired records", deleted);
            }
        } catch (Exception e) {
            log.error("IdempotencyCleanup: cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 获取服务状态
     */
    public java.util.Map<String, Object> getStatus() {
        return java.util.Map.of(
                "cleanup_interval_seconds", cleanupIntervalSeconds,
                "cleanup_batch_size", cleanupBatchSize
        );
    }
}
