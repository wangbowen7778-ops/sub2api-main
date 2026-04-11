package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.account.mapper.AccountGroupMapper;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 账号选择算法服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSelector {

    private final AccountMapper accountMapper;
    private final AccountGroupMapper accountGroupMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String STICKY_SESSION_KEY_PREFIX = "sticky:account:";
    private static final long STICKY_SESSION_TTL_SECONDS = 300;

    /**
     * 策略枚举
     */
    public enum Strategy {
        RANDOM,
        ROUND_ROBIN,
        PRIORITY,
        LEAST_USED,
        LEAST_CONCURRENCY
    }

    /**
     * 选择最优账号 (按分组)
     */
    public Account selectAccount(Long groupId, Strategy strategy, String stickySessionKey) {
        // 如果有粘性会话Key，优先使用之前分配的账号
        if (stickySessionKey != null && !stickySessionKey.isEmpty()) {
            Account stickyAccount = getStickyAccount(stickySessionKey, groupId);
            if (stickyAccount != null && isAccountUsable(stickyAccount)) {
                log.debug("使用粘性会话账号: accountId={}", stickyAccount.getId());
                return stickyAccount;
            }
        }

        // 获取分组下所有可用账号
        List<Account> availableAccounts = getAvailableAccountsByGroup(groupId);
        if (availableAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALL_UNAVAILABLE);
        }

        // 根据策略选择
        Account selected = selectByStrategy(availableAccounts, strategy, groupId);

        // 保存粘性会话
        if (stickySessionKey != null && selected != null) {
            saveStickyAccount(stickySessionKey, groupId, selected.getId());
        }

        return selected;
    }

    /**
     * 选择最优账号 (按平台)
     */
    public Account selectAccountByPlatform(String platform, Strategy strategy, String stickySessionKey) {
        // 如果有粘性会话Key，优先使用之前分配的账号
        if (stickySessionKey != null && !stickySessionKey.isEmpty()) {
            Account stickyAccount = getStickyAccountByPlatform(stickySessionKey, platform);
            if (stickyAccount != null && isAccountUsable(stickyAccount)) {
                log.debug("使用粘性会话账号: accountId={}", stickyAccount.getId());
                return stickyAccount;
            }
        }

        // 获取平台下所有可用账号
        List<Account> availableAccounts = getAvailableAccountsByPlatform(platform);
        if (availableAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALL_UNAVAILABLE);
        }

        // 根据策略选择
        Account selected = selectByStrategy(availableAccounts, strategy, null);

        // 保存粘性会话
        if (stickySessionKey != null && selected != null) {
            saveStickyAccountByPlatform(stickySessionKey, platform, selected.getId());
        }

        return selected;
    }

    /**
     * 根据分组获取可用账号
     */
    private List<Account> getAvailableAccountsByGroup(Long groupId) {
        // 先获取分组下的所有账号ID
        List<Long> accountIds = accountGroupMapper.selectAccountIdsByGroupId(groupId);
        if (accountIds == null || accountIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Account::getId, accountIds)
                .eq(Account::getStatus, AccountStatus.ACTIVE.getValue())
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                // 排除速率限制中的账号
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, LocalDateTime.now())
                )
                // 排除过载的账号
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, LocalDateTime.now())
                )
                // 排除临时不可调度的账号
                .and(w -> w
                        .isNull(Account::getTempUnschedulableUntil)
                        .or()
                        .le(Account::getTempUnschedulableUntil, LocalDateTime.now())
                )
                // 检查过期
                .and(w -> w
                        .isNull(Account::getExpiresAt)
                        .or()
                        .gt(Account::getExpiresAt, LocalDateTime.now())
                )
                .orderByAsc(Account::getPriority);

        return accountMapper.selectList(wrapper);
    }

    /**
     * 根据平台获取可用账号
     */
    private List<Account> getAvailableAccountsByPlatform(String platform) {
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Account::getPlatform, platform)
                .eq(Account::getStatus, AccountStatus.ACTIVE.getValue())
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, LocalDateTime.now())
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, LocalDateTime.now())
                )
                .and(w -> w
                        .isNull(Account::getTempUnschedulableUntil)
                        .or()
                        .le(Account::getTempUnschedulableUntil, LocalDateTime.now())
                )
                .orderByAsc(Account::getPriority);

        return accountMapper.selectList(wrapper);
    }

    /**
     * 检查账号是否可用
     */
    private boolean isAccountUsable(Account account) {
        if (!AccountStatus.ACTIVE.getValue().equals(account.getStatus())) {
            return false;
        }
        if (Boolean.FALSE.equals(account.getSchedulable())) {
            return false;
        }
        if (account.getDeletedAt() != null) {
            return false;
        }
        if (account.getRateLimitResetAt() != null && account.getRateLimitResetAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (account.getOverloadUntil() != null && account.getOverloadUntil().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (account.getTempUnschedulableUntil() != null && account.getTempUnschedulableUntil().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (account.getExpiresAt() != null && account.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * 根据策略选择账号
     */
    private Account selectByStrategy(List<Account> accounts, Strategy strategy, Long groupId) {
        if (accounts.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case RANDOM:
                return accounts.get((int) (Math.random() * accounts.size()));
            case ROUND_ROBIN:
                return selectByRoundRobin(accounts, groupId);
            case PRIORITY:
                return accounts.stream()
                        .min((a, b) -> {
                            int pa = a.getPriority() != null ? a.getPriority() : 50;
                            int pb = b.getPriority() != null ? b.getPriority() : 50;
                            return Integer.compare(pa, pb);
                        })
                        .orElse(accounts.get(0));
            case LEAST_USED:
                return accounts.stream()
                        .min((a, b) -> {
                            long ua = a.getLastUsedAt() != null ? a.getLastUsedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0;
                            long ub = b.getLastUsedAt() != null ? b.getLastUsedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0;
                            return Long.compare(ua, ub);
                        })
                        .orElse(accounts.get(0));
            case LEAST_CONCURRENCY:
                return accounts.stream()
                        .min((a, b) -> {
                            int ca = a.getConcurrency() != null ? a.getConcurrency() : 0;
                            int cb = b.getConcurrency() != null ? b.getConcurrency() : 0;
                            return Integer.compare(ca, cb);
                        })
                        .orElse(accounts.get(0));
            default:
                return accounts.get(0);
        }
    }

    /**
     * 轮询选择
     */
    private Account selectByRoundRobin(List<Account> accounts, Long groupId) {
        String counterKey = "dispatch:round_robin:" + (groupId != null ? groupId : "default");
        Long index = redisTemplate.opsForValue().increment(counterKey);
        if (index == null) {
            index = 1L;
        }
        int selectedIndex = (int) ((index - 1) % accounts.size());
        return accounts.get(selectedIndex);
    }

    /**
     * 获取粘性会话账号 (按分组)
     */
    private Account getStickyAccount(String sessionKey, Long groupId) {
        String key = STICKY_SESSION_KEY_PREFIX + "group:" + groupId + ":" + sessionKey;
        String accountIdStr = redisTemplate.opsForValue().get(key);
        if (accountIdStr == null) {
            return null;
        }
        try {
            Long accountId = Long.parseLong(accountIdStr);
            Account account = accountMapper.selectById(accountId);
            if (account != null && isAccountUsable(account)) {
                return account;
            }
        } catch (NumberFormatException e) {
            log.warn("粘性会话账号ID无效: {}", accountIdStr);
        }
        return null;
    }

    /**
     * 获取粘性会话账号 (按平台)
     */
    private Account getStickyAccountByPlatform(String sessionKey, String platform) {
        String key = STICKY_SESSION_KEY_PREFIX + "platform:" + platform + ":" + sessionKey;
        String accountIdStr = redisTemplate.opsForValue().get(key);
        if (accountIdStr == null) {
            return null;
        }
        try {
            Long accountId = Long.parseLong(accountIdStr);
            Account account = accountMapper.selectById(accountId);
            if (account != null && isAccountUsable(account)) {
                return account;
            }
        } catch (NumberFormatException e) {
            log.warn("粘性会话账号ID无效: {}", accountIdStr);
        }
        return null;
    }

    /**
     * 保存粘性会话账号 (按分组)
     */
    private void saveStickyAccount(String sessionKey, Long groupId, Long accountId) {
        String key = STICKY_SESSION_KEY_PREFIX + "group:" + groupId + ":" + sessionKey;
        redisTemplate.opsForValue().set(key, accountId.toString(), STICKY_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 保存粘性会话账号 (按平台)
     */
    private void saveStickyAccountByPlatform(String sessionKey, String platform, Long accountId) {
        String key = STICKY_SESSION_KEY_PREFIX + "platform:" + platform + ":" + sessionKey;
        redisTemplate.opsForValue().set(key, accountId.toString(), STICKY_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
