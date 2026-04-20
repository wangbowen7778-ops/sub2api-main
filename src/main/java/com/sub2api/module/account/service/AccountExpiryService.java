package com.sub2api.module.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 账号过期服务
 * 当自动暂停启用时，定期暂停已过期的账号
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountExpiryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccountExpiryService.class);

    private final AccountService accountService;

    /**
     * 默认检查间隔（毫秒）
     */
    private static final long DEFAULT_INTERVAL_MS = 60_000;

    /**
     * 上下文超时时间（秒）
     */
    private static final int CONTEXT_TIMEOUT_SECONDS = 5;

    /**
     * 是否正在运行
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 检查间隔（毫秒）
     */
    @Value("${account.expiry.check-interval:60000}")
    private long intervalMs;

    /**
     * 是否已启动
     */
    private volatile boolean started = false;

    /**
     * 启动服务
     */
    public void start() {
        if (started || intervalMs <= 0) {
            return;
        }
        started = true;
        running.set(true);
        log.info("AccountExpiryService started with interval={}ms", intervalMs);
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
        log.info("AccountExpiryService stopped");
    }

    /**
     * 定期执行过期账号检查
     */
    @Scheduled(fixedDelayString = "${account.expiry.check-interval:60000}")
    public void runOnce() {
        if (!started || !running.get()) {
            return;
        }

        try {
            int updated = accountService.autoPauseExpiredAccounts(OffsetDateTime.now());
            if (updated > 0) {
                log.info("Auto paused {} expired accounts", updated);
            }
        } catch (Exception e) {
            log.error("Auto pause expired accounts failed", e);
        }
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
