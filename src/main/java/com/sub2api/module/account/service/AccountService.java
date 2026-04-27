package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Account service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class AccountService extends ServiceImpl<AccountMapper, Account> {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountMapper accountMapper;

    /**
     * Find account by ID
     */
    public Account findById(Long id) {
        Account account = getById(id);
        if (account == null || account.getDeletedAt() != null) {
            return null;
        }
        return account;
    }

    /**
     * Find active accounts by platform
     */
    public List<Account> listActiveByPlatform(String platform) {
        return list(new LambdaQueryWrapper<Account>()
                .eq(Account::getPlatform, platform)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .orderByAsc(Account::getPriority));
    }

    /**
     * Find accounts by group ID
     */
    public List<Account> listByGroupId(Long groupId) {
        // Query through account_groups table
        return list(new LambdaQueryWrapper<Account>()
                .eq(Account::getStatus, "active")
                .isNull(Account::getDeletedAt));
    }

    /**
     * Query all schedulable accounts
     */
    public List<Account> listSchedulable(String platform) {
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<Account>()
                .eq(Account::getPlatform, platform)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .isNull(Account::getRateLimitResetAt)
                .or(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, OffsetDateTime.now())
                )
                .orderByAsc(Account::getPriority);

        return list(wrapper);
    }

    /**
     * Query all OAuth accounts
     * Note: Go backend uses 'type' field to identify OAuth vs API key accounts
     */
    public List<Account> listAllOAuthAccounts() {
        return list(new LambdaQueryWrapper<Account>()
                .eq(Account::getType, "oauth")
                .isNull(Account::getDeletedAt));
    }

    /**
     * Query accounts by platform
     */
    public List<Account> listByPlatform(String platform) {
        return list(new LambdaQueryWrapper<Account>()
                .eq(Account::getPlatform, platform)
                .isNull(Account::getDeletedAt)
                .orderByAsc(Account::getPriority));
    }

    /**
     * Reset account - clears rate limits and overload status
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetAccount(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setRateLimitedAt(null);
        updateAccount.setRateLimitResetAt(null);
        updateAccount.setOverloadUntil(null);
        updateAccount.setErrorMessage(null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
        log.info("Reset account: accountId={}", accountId);
    }

    /**
     * Create account
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(Account account) {
        account.setStatus("active");
        account.setSchedulable(true);
        account.setAutoPauseOnExpired(true);
        if (account.getConcurrency() == null) {
            account.setConcurrency(3);
        }
        if (account.getPriority() == null) {
            account.setPriority(50);
        }
        if (account.getRateMultiplier() == null) {
            account.setRateMultiplier(BigDecimal.ONE);
        }
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());

        if (!save(account)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create account");
        }

        log.info("Created account: accountId={}, platform={}, name={}", account.getId(), account.getPlatform(), account.getName());
        return account;
    }

    /**
     * Update account
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(Account account) {
        Account existing = findById(account.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        account.setUpdatedAt(OffsetDateTime.now());
        updateById(account);
        log.info("Updated account: accountId={}", account.getId());
    }

    /**
     * Update account status
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long accountId, String status) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setStatus(status);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
        log.info("Updated account status: accountId={}, status={}", accountId, status);
    }

    /**
     * Update last used time
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLastUsed(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setLastUsedAt(OffsetDateTime.now());
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set rate limited
     */
    @Transactional(rollbackFor = Exception.class)
    public void setRateLimited(Long accountId, OffsetDateTime resetAt) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setRateLimitedAt(OffsetDateTime.now());
        updateAccount.setRateLimitResetAt(resetAt != null ? resetAt : null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Clear rate limited
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearRateLimited(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setRateLimitedAt(null);
        updateAccount.setRateLimitResetAt(null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set overload
     */
    @Transactional(rollbackFor = Exception.class)
    public void setOverload(Long accountId, OffsetDateTime until) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setOverloadUntil(until != null ? until : null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set temporary unschedulable
     */
    @Transactional(rollbackFor = Exception.class)
    public void setTempUnschedulable(Long accountId, OffsetDateTime until, String reason) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setTempUnschedulableUntil(until != null ? until : null);
        updateAccount.setTempUnschedulableReason(reason);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set error status
     */
    @Transactional(rollbackFor = Exception.class)
    public void setError(Long accountId, String errorMessage) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setStatus("error");
        updateAccount.setErrorMessage(errorMessage);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Clear error status
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearError(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setStatus("active");
        updateAccount.setErrorMessage(null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Get supported models for account
     */
    public List<String> getSupportedModels(Account account) {
        if (account == null || account.getPlatform() == null) {
            return List.of();
        }
        String platform = account.getPlatform().toLowerCase();
        return switch (platform) {
            case "anthropic", "claude" -> List.of(
                    "claude-opus-4-6-20251101",
                    "claude-sonnet-4-6-20251101",
                    "claude-haiku-3-20250514"
            );
            case "openai" -> List.of(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo"
            );
            case "gemini" -> List.of(
                    "gemini-1.5-pro",
                    "gemini-1.5-flash"
            );
            case "antigravity" -> List.of(
                    "claude-sonnet-4-20250514",
                    "claude-haiku-3-20250514"
            );
            default -> List.of();
        };
    }

    /**
     * Soft delete account
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setDeletedAt(OffsetDateTime.now());
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
        log.info("Deleted account: accountId={}", accountId);
    }

    /**
     * Check if account is expired
     */
    public boolean isExpired(Account account) {
        if (account.getExpiresAt() == null) {
            return false;
        }
        return OffsetDateTime.now().isAfter(account.getExpiresAt());
    }

    /**
     * Get decrypted API Key
     */
    public String getDecryptedApiKey(Account account) {
        if (account.getCredentials() == null) {
            return null;
        }
        Object apiKey = account.getCredentials().get("api_key");
        return apiKey != null ? apiKey.toString() : null;
    }

    /**
     * Get model mapping from account credentials
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, String> getModelMapping(Account account) {
        if (account.getCredentials() == null) {
            return java.util.Collections.emptyMap();
        }
        Object mapping = account.getCredentials().get("model_mapping");
        if (mapping instanceof java.util.Map) {
            java.util.Map<String, Object> rawMapping = (java.util.Map<String, Object>) mapping;
            java.util.Map<String, String> result = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Object> entry : rawMapping.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue().toString());
                }
            }
            return result;
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * Update session window for sticky session management
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionWindow(Long accountId, OffsetDateTime start, OffsetDateTime end, String status) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setSessionWindowStart(start != null ? start : null);
        updateAccount.setSessionWindowEnd(end != null ? end : null);
        updateAccount.setSessionWindowStatus(status);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Clear session window
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearSessionWindow(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setSessionWindowStart(null);
        updateAccount.setSessionWindowEnd(null);
        updateAccount.setSessionWindowStatus(null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Clear temporary unschedulable status
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearTempUnschedulable(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setTempUnschedulableUntil(null);
        updateAccount.setTempUnschedulableReason(null);
        updateAccount.setUpdatedAt(OffsetDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Auto pause expired accounts
     * Queries accounts where:
     * 1. autoPauseOnExpired = true
     * 2. expiresAt <= now
     * 3. status != "paused"
     *
     * @param now current time
     * @return number of accounts paused
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoPauseExpiredAccounts(OffsetDateTime now) {
        OffsetDateTime nowOffset = now != null ? now : OffsetDateTime.now();
        // Find accounts that are expired but not yet paused
        List<Account> expiredAccounts = list(new LambdaQueryWrapper<Account>()
                .eq(Account::getAutoPauseOnExpired, true)
                .isNotNull(Account::getExpiresAt)
                .le(Account::getExpiresAt, nowOffset)
                .ne(Account::getStatus, "paused")
                .isNull(Account::getDeletedAt));

        if (expiredAccounts.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Account account : expiredAccounts) {
            Account updateAccount = new Account();
            updateAccount.setId(account.getId());
            updateAccount.setStatus("paused");
            updateAccount.setUpdatedAt(nowOffset);
            if (updateById(updateAccount)) {
                count++;
                log.info("Auto paused expired account: accountId={}, name={}, expiredAt={}",
                        account.getId(), account.getName(), account.getExpiresAt());
            }
        }

        return count;
    }

    /**
     * Batch update last used time for multiple accounts
     * Used by DeferredService for batching updates
     *
     * @param accountIdToLastUsed map of account ID to last used time
     * @return number of accounts updated
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateLastUsed(java.util.Map<Long, OffsetDateTime> accountIdToLastUsed) {
        if (accountIdToLastUsed == null || accountIdToLastUsed.isEmpty()) {
            return 0;
        }

        int count = 0;
        OffsetDateTime now = OffsetDateTime.now();

        for (java.util.Map.Entry<Long, OffsetDateTime> entry : accountIdToLastUsed.entrySet()) {
            Account updateAccount = new Account();
            updateAccount.setId(entry.getKey());
            updateAccount.setLastUsedAt(entry.getValue() != null ? entry.getValue() : null);
            updateAccount.setUpdatedAt(now);
            if (updateById(updateAccount)) {
                count++;
            }
        }

        if (count > 0) {
            log.info("BatchUpdateLastUsed flushed {} accounts", count);
        }

        return count;
    }
}
