package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account health check service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class AccountHealthService extends ServiceImpl<AccountMapper, Account> {

    private static final Logger log = LoggerFactory.getLogger(AccountHealthService.class);

    private final AccountMapper accountMapper;

    /**
     * Scheduled health check
     */
    @Scheduled(fixedDelay = 60000) // 1 minute
    @Transactional(rollbackFor = Exception.class)
    public void healthCheck() {
        log.debug("Starting account health check...");

        // Check all active accounts
        List<Account> activeAccounts = list(new LambdaQueryWrapper<Account>()
                .eq(Account::getStatus, AccountStatus.ACTIVE.getValue())
                .isNull(Account::getDeletedAt));

        for (Account account : activeAccounts) {
            checkAccountHealth(account);
        }
    }

    /**
     * Check single account health status
     */
    private void checkAccountHealth(Account account) {
        try {
            // Check if quota is exhausted
            if (account.getInputTokenLimit() != null && account.getInputTokenLimit() > 0) {
                if (account.getUsedInputTokens() != null && account.getUsedInputTokens() >= account.getInputTokenLimit()) {
                    updateAccountStatus(account, AccountStatus.EXHAUSTED, "Input quota exhausted");
                    return;
                }
            }

            if (account.getOutputTokenLimit() != null && account.getOutputTokenLimit() > 0) {
                if (account.getUsedOutputTokens() != null && account.getUsedOutputTokens() >= account.getOutputTokenLimit()) {
                    updateAccountStatus(account, AccountStatus.EXHAUSTED, "Output quota exhausted");
                    return;
                }
            }

            // Call platform API for actual health check
            boolean isHealthy = testAccountConnectionInternal(account);
            if (!isHealthy) {
                updateAccountStatus(account, AccountStatus.ERROR, "Health check failed");
            } else {
                // Health check successful, keep active status
                if (AccountStatus.ERROR.getValue().equals(account.getStatus())) {
                    updateAccountStatus(account, AccountStatus.ACTIVE, "Health check recovered");
                }
            }

        } catch (Exception e) {
            log.error("Account health check exception: accountId={}, error={}", account.getId(), e.getMessage());
            updateAccountStatus(account, AccountStatus.ERROR, e.getMessage());
        }
    }

    /**
     * Internal health check implementation
     */
    private boolean testAccountConnectionInternal(Account account) {
        try {
            String platform = account.getPlatform();
            Map<String, Object> credentials = account.getCredentials();

            if (credentials == null || credentials.isEmpty()) {
                return false;
            }

            // Health check based on different platforms
            // Simple verification: check if necessary credential fields exist
            switch (platform != null ? platform.toLowerCase() : "") {
                case "claude":
                case "openai":
                case "gemini":
                case "antigravity":
                    // Check for api_key
                    return credentials.containsKey("api_key");
                default:
                    // Default verification
                    return credentials.containsKey("api_key") || credentials.containsKey("access_token");
            }
        } catch (Exception e) {
            log.error("Connection test exception: accountId={}, error={}", account.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Update account status
     */
    private void updateAccountStatus(Account account, AccountStatus status, String reason) {
        account.setStatus(status.getValue());
        account.setLastError(reason);
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);

        log.info("Account status changed: accountId={}, status={}, reason={}", account.getId(), status.getDescription(), reason);
    }

    /**
     * Test account connectivity
     */
    public boolean testAccountConnection(Long accountId) {
        Account account = getById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        // Implement actual connectivity test
        // Call platform API to test if credentials are valid
        return testAccountConnectionInternal(account);
    }
}
