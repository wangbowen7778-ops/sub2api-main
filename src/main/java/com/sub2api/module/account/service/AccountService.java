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
import java.time.LocalDateTime;
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
                        .le(Account::getOverloadUntil, LocalDateTime.now())
                )
                .orderByAsc(Account::getPriority);

        return list(wrapper);
    }

    /**
     * Query all accounts that need refresh (OAuth accounts with refresh token)
     */
    public List<Account> listAllWithRefreshToken() {
        return list(new LambdaQueryWrapper<Account>()
                .isNotNull(Account::getRefreshToken)
                .ne(Account::getRefreshToken, "")
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
     * Reset token usage
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetTokenUsage(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setUsedInputTokens(0L);
        updateAccount.setUsedOutputTokens(0L);
        updateAccount.setUpdatedAt(LocalDateTime.now());
        updateById(updateAccount);
        log.info("Reset account token usage: accountId={}", accountId);
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
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

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
        account.setUpdatedAt(LocalDateTime.now());
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
        updateAccount.setUpdatedAt(LocalDateTime.now());
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
        updateAccount.setLastUsedAt(LocalDateTime.now());
        updateAccount.setUpdatedAt(LocalDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set rate limited
     */
    @Transactional(rollbackFor = Exception.class)
    public void setRateLimited(Long accountId, LocalDateTime resetAt) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setRateLimitedAt(LocalDateTime.now());
        updateAccount.setRateLimitResetAt(resetAt);
        updateAccount.setUpdatedAt(LocalDateTime.now());
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
        updateAccount.setUpdatedAt(LocalDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set overload
     */
    @Transactional(rollbackFor = Exception.class)
    public void setOverload(Long accountId, LocalDateTime until) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setOverloadUntil(until);
        updateAccount.setUpdatedAt(LocalDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Set temporary unschedulable
     */
    @Transactional(rollbackFor = Exception.class)
    public void setTempUnschedulable(Long accountId, LocalDateTime until, String reason) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setTempUnschedulableUntil(until);
        updateAccount.setTempUnschedulableReason(reason);
        updateAccount.setUpdatedAt(LocalDateTime.now());
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
        updateAccount.setUpdatedAt(LocalDateTime.now());
        updateById(updateAccount);
    }

    /**
     * Soft delete account
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        Account updateAccount = new Account();
        updateAccount.setId(accountId);
        updateAccount.setDeletedAt(LocalDateTime.now());
        updateAccount.setUpdatedAt(LocalDateTime.now());
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
        return LocalDateTime.now().isAfter(account.getExpiresAt());
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
}
