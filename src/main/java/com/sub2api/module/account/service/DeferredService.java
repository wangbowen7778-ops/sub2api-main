package com.sub2api.module.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 延迟批量更新服务
 * 用于批量更新账号的最后使用时间，避免频繁单条更新数据库
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeferredService {

    private final AccountService accountService;

    /**
     * 默认刷新间隔（毫秒）
     */
    private static final long DEFAULT_INTERVAL_MS = 5000;

    /**
     * 是否正在运行
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 延迟更新的最后使用时间映射
     */
    private final Map<Long, OffsetDateTime> lastUsedUpdates = new ConcurrentHashMap<>();

    /**
     * 刷新间隔（毫秒）
     */
    @Value("${deferred.flush-interval:5000}")
    private long intervalMs;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 启动服务
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        running.set(true);
        log.info("DeferredService started with interval={}ms", intervalMs);
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (!started) {
            return;
        }
        running.set(false);
        started = false;
        // 刷新剩余的更新
        flushLastUsed();
        log.info("DeferredService stopped");
    }

    /**
     * 调度最后使用时间更新
     */
    public void scheduleLastUsedUpdate(Long accountId) {
        lastUsedUpdates.put(accountId, OffsetDateTime.now());
    }

    /**
     * 刷新最后使用时间更新
     */
    @Scheduled(fixedDelayString = "${deferred.flush-interval:5000}")
    public void flushLastUsed() {
        if (!started || !running.get()) {
            return;
        }

        if (lastUsedUpdates.isEmpty()) {
            return;
        }

        // 提取所有待更新项
        Map<Long, OffsetDateTime> updates = new ConcurrentHashMap<>();
        lastUsedUpdates.forEach((id, time) -> {
            updates.put(id, time);
            lastUsedUpdates.remove(id);
        });

        if (updates.isEmpty()) {
            return;
        }

        try {
            int count = accountService.batchUpdateLastUsed(updates);
            if (count > 0) {
                log.info("DeferredService: flushed {} last_used updates", count);
            }
        } catch (Exception e) {
            log.error("DeferredService: BatchUpdateLastUsed failed ({} accounts): {}",
                    updates.size(), e.getMessage());
            // 将失败的更新重新放回队列
            updates.forEach(lastUsedUpdates::put);
        }
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取待处理更新数量
     */
    public int getPendingUpdatesCount() {
        return lastUsedUpdates.size();
    }
}
