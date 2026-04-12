package com.sub2api.module.gateway.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故障转移状态
 * 跨循环迭代共享的 failover 状态
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Data
public class FailoverState {

    /**
     * 最大账号切换次数
     */
    private int maxSwitches;

    /**
     * 当前切换次数
     */
    private int switchCount;

    /**
     * 失败的账号 ID 集合
     */
    private Map<Long, Boolean> failedAccountIds;

    /**
     * 同账号重试计数
     */
    private Map<Long, Integer> sameAccountRetryCount;

    /**
     * 上一个 failover 错误
     */
    private UpstreamFailoverError lastFailoverErr;

    /**
     * 是否强制缓存计费
     */
    private boolean forceCacheBilling;

    /**
     * 是否有绑定的粘性会话
     */
    private boolean hasBoundSession;

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
     * 创建故障转移状态
     */
    public FailoverState(int maxSwitches, boolean hasBoundSession) {
        this.maxSwitches = maxSwitches;
        this.hasBoundSession = hasBoundSession;
        this.switchCount = 0;
        this.failedAccountIds = new ConcurrentHashMap<>();
        this.sameAccountRetryCount = new ConcurrentHashMap<>();
        this.forceCacheBilling = false;
    }

    /**
     * 获取下一个失败账号 ID 集合（用于选号时排除）
     */
    public Long[] getFailedAccountIdArray() {
        return failedAccountIds.keySet().toArray(new Long[0]);
    }

    /**
     * 检查是否已达到最大切换次数
     */
    public boolean isExhausted() {
        return switchCount >= maxSwitches;
    }

    /**
     * 重置失败账号列表（用于单账号退避重试）
     */
    public void resetFailedAccounts() {
        failedAccountIds.clear();
    }

    /**
     * 清除指定账号的重试计数
     */
    public void clearRetryCount(long accountId) {
        sameAccountRetryCount.remove(accountId);
    }

    /**
     * 获取指定账号的重试次数
     */
    public int getRetryCount(long accountId) {
        Integer count = sameAccountRetryCount.get(accountId);
        return count != null ? count : 0;
    }

    /**
     * 增加指定账号的重试次数
     */
    public int incrementRetryCount(long accountId) {
        int count = getRetryCount(accountId) + 1;
        sameAccountRetryCount.put(accountId, count);
        return count;
    }

    /**
     * 检查是否需要强制缓存计费
     */
    public boolean needForceCacheBilling() {
        return hasBoundSession || (lastFailoverErr != null && lastFailoverErr.isForceCacheBilling());
    }
}
