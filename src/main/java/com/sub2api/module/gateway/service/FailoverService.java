package com.sub2api.module.gateway.service;

import com.sub2api.module.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 故障转移服务
 * 处理上游错误的故障转移逻辑
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailoverService {

    private final AccountService accountService;

    /**
     * 同账号最大重试次数
     */
    private static final int MAX_SAME_ACCOUNT_RETRIES = 3;

    /**
     * 同账号重试间隔（毫秒）
     */
    private static final long SAME_ACCOUNT_RETRY_DELAY_MS = 500;

    /**
     * 单账号退避延迟（毫秒）
     */
    private static final long SINGLE_ACCOUNT_BACKOFF_DELAY_MS = 2000;

    /**
     * 默认最大切换次数
     */
    private static final int DEFAULT_MAX_SWITCHES = 3;

    /**
     * 创建默认的故障转移状态
     */
    public FailoverState createDefaultState() {
        return new FailoverState(DEFAULT_MAX_SWITCHES, false);
    }

    /**
     * 创建故障转移状态
     */
    public FailoverState createState(int maxSwitches, boolean hasBoundSession) {
        return new FailoverState(maxSwitches, hasBoundSession);
    }

    /**
     * 处理故障转移错误，返回下一步动作
     *
     * @param ctx context
     * @param state 故障转移状态
     * @param accountId 账号ID
     * @param platform 平台
     * @param failoverErr 故障转移错误
     * @return 下一步动作
     */
    public FailoverAction handleFailoverError(
            Context ctx,
            FailoverState state,
            long accountId,
            String platform,
            UpstreamFailoverError failoverErr) {

        state.setLastFailoverErr(failoverErr);

        // 缓存计费判断
        if (state.needForceCacheBilling()) {
            state.setForceCacheBilling(true);
        }

        // 同账号重试：对 RetryableOnSameAccount 的临时性错误，先在同一账号上重试
        if (failoverErr.isRetryableOnSameAccount() && state.getRetryCount(accountId) < MAX_SAME_ACCOUNT_RETRIES) {
            int retryCount = state.incrementRetryCount(accountId);
            log.warn("gateway.failover_same_account_retry: accountId={}, status={}, retryCount={}, max={}",
                    accountId, failoverErr.getStatusCode(), retryCount, MAX_SAME_ACCOUNT_RETRIES);

            if (!sleepWithContext(ctx, SAME_ACCOUNT_RETRY_DELAY_MS)) {
                return FailoverAction.CANCELED;
            }
            return FailoverAction.CONTINUE;
        }

        // 同账号重试用尽，执行临时封禁
        if (failoverErr.isRetryableOnSameAccount()) {
            tempUnscheduleRetryableError(accountId, failoverErr);
        }

        // 加入失败列表
        state.getFailedAccountIds().put(accountId, true);

        // 检查是否耗尽
        if (state.getSwitchCount() >= state.getMaxSwitches()) {
            return FailoverAction.EXHAUSTED;
        }

        // 递增切换计数
        state.setSwitchCount(state.getSwitchCount() + 1);
        log.warn("gateway.failover_switch_account: accountId={}, status={}, switchCount={}, maxSwitches={}",
                accountId, failoverErr.getStatusCode(), state.getSwitchCount(), state.getMaxSwitches());

        // Antigravity 平台换号线性递增延时
        if ("antigravity".equalsIgnoreCase(platform)) {
            long delay = (state.getSwitchCount() - 1) * 1000;
            if (delay > 0 && !sleepWithContext(ctx, delay)) {
                return FailoverAction.CANCELED;
            }
        }

        return FailoverAction.CONTINUE;
    }

    /**
     * 处理选号失败（所有候选账号都在排除列表中）时的退避重试决策
     *
     * @param ctx context
     * @param state 故障转移状态
     * @return 下一步动作
     */
    public FailoverAction handleSelectionExhausted(Context ctx, FailoverState state) {
        if (state.getLastFailoverErr() != null &&
                state.getLastFailoverErr().getStatusCode() == 503 &&
                state.getSwitchCount() <= state.getMaxSwitches()) {

            log.warn("gateway.failover_single_account_backoff: delay={}ms, switchCount={}, maxSwitches={}",
                    SINGLE_ACCOUNT_BACKOFF_DELAY_MS, state.getSwitchCount(), state.getMaxSwitches());

            if (!sleepWithContext(ctx, SINGLE_ACCOUNT_BACKOFF_DELAY_MS)) {
                return FailoverAction.CANCELED;
            }

            log.warn("gateway.failover_single_account_retry: switchCount={}, maxSwitches={}",
                    state.getSwitchCount(), state.getMaxSwitches());

            state.resetFailedAccounts();
            return FailoverAction.CONTINUE;
        }
        return FailoverAction.EXHAUSTED;
    }

    /**
     * 对 RetryableOnSameAccount 类型的 failover 错误触发临时封禁
     */
    private void tempUnscheduleRetryableError(long accountId, UpstreamFailoverError failoverErr) {
        if (failoverErr == null || !failoverErr.isRetryableOnSameAccount()) {
            return;
        }

        // 根据状态码选择封禁策略
        switch (failoverErr.getStatusCode()) {
            case 400:
                // Google 间歇性 400，封禁 30 秒
                accountService.setTempUnschedulable(accountId,
                        java.time.OffsetDateTime.now().plusSeconds(30),
                        "Retryable 400 error");
                break;
            case 429:
                // Rate limit，封禁 1 分钟
                accountService.setTempUnschedulable(accountId,
                        java.time.OffsetDateTime.now().plusMinutes(1),
                        "Rate limit");
                break;
            case 500:
            case 502:
            case 503:
            case 504:
                // 服务器错误，封禁 30 秒
                accountService.setTempUnschedulable(accountId,
                        java.time.OffsetDateTime.now().plusSeconds(30),
                        "Upstream error: " + failoverErr.getStatusCode());
                break;
            default:
                // 其他错误，封禁 1 分钟
                accountService.setTempUnschedulable(accountId,
                        java.time.OffsetDateTime.now().plusMinutes(1),
                        "Retryable error: " + failoverErr.getStatusCode());
        }
    }

    /**
     * 等待指定时长，返回 false 表示 context 已取消
     */
    private boolean sleepWithContext(Context ctx, long delayMs) {
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return !ctx.isCanceled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Context 接口
     */
    public interface Context {
        boolean isCanceled();
    }

    /**
     * 简单的 Context 实现
     */
    public static class SimpleContext implements Context {
        private volatile boolean canceled = false;

        public void cancel() {
            this.canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }
    }

    /**
     * Java.util.concurrent 的 Context 实现
     */
    public static class CoTaskContext implements Context {
        private final java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean(false);

        public void cancel() {
            canceled.set(true);
        }

        @Override
        public boolean isCanceled() {
            return canceled.get();
        }
    }

    /**
     * FailoverAction 表示 failover 错误处理后的下一步动作
     */
    public enum FailoverAction {
        /**
         * 继续循环（同账号重试或切换账号，调用方统一 continue）
         */
        CONTINUE,

        /**
         * 切换次数耗尽（调用方应返回错误响应）
         */
        EXHAUSTED,

        /**
         * Context 已取消（调用方应直接 return）
         */
        CANCELED
    }
}
