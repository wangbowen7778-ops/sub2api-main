package com.sub2api.module.account.service;

import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Account credential refresh service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class AccountRefreshService {

    private static final Logger log = LoggerFactory.getLogger(AccountRefreshService.class);

    private final AccountService accountService;

    /**
     * Refresh OAuth credentials (scheduled task)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional(rollbackFor = Exception.class)
    public void refreshOAuthCredentials() {
        log.debug("Starting OAuth credential refresh...");

        List<Account> accounts = accountService.listAllWithRefreshToken();
        for (Account account : accounts) {
            try {
                refreshAccountCredential(account);
            } catch (Exception e) {
                log.error("Failed to refresh account credential: accountId={}, error={}", account.getId(), e.getMessage());
                handleRefreshFailure(account, e.getMessage());
            }
        }
    }

    /**
     * Refresh single account credential
     */
    private void refreshAccountCredential(Account account) {
        // Check if refresh is needed
        if (account.getCredentialExpiredAt() == null ||
                account.getCredentialExpiredAt().isAfter(LocalDateTime.now().plusHours(1))) {
            return;
        }

        // Call corresponding refresh API based on different platforms
        String platform = account.getPlatform();
        String refreshToken = account.getRefreshToken();

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Account has no refresh token, skipping refresh: accountId={}, platform={}", account.getId(), platform);
            return;
        }

        try {
            // OAuth refresh logic - call corresponding refresh API based on different platforms
            // This is a placeholder implementation, actual needs to implement refresh logic for different platforms
            log.info("Refreshing account credential: accountId={}, platform={}", account.getId(), platform);

            // Simulate successful refresh - should call platform API to get new credentials
            // Update refresh time and error count
            account.setLastRefreshAt(LocalDateTime.now());
            account.setRefreshErrorCount(0);
            account.setStatus(AccountStatus.ACTIVE.getValue());
            account.setCredentialExpiredAt(LocalDateTime.now().plusDays(30)); // Assume validity extended by 30 days after refresh
            accountService.updateById(account);

        } catch (Exception e) {
            log.error("Failed to refresh account credential: accountId={}, error={}", account.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Handle refresh failure
     */
    private void handleRefreshFailure(Account account, String error) {
        int errorCount = account.getRefreshErrorCount() != null ? account.getRefreshErrorCount() : 0;
        account.setRefreshErrorCount(errorCount + 1);
        account.setLastError(error);

        if (errorCount >= 3) {
            account.setStatus(AccountStatus.CREDENTIAL_EXPIRED.getValue());
            log.warn("Account credential refresh failed too many times, disabled: accountId={}", account.getId());
        }

        accountService.updateById(account);
    }

    /**
     * Manual refresh account credential
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualRefresh(Long accountId) {
        Account account = accountService.getById(accountId);
        if (account == null) {
            throw new RuntimeException("Account not found");
        }

        try {
            refreshAccountCredential(account);
        } catch (Exception e) {
            log.error("Manual refresh account credential failed: accountId={}, error={}", accountId, e.getMessage());
            handleRefreshFailure(account, e.getMessage());
            throw new RuntimeException("Refresh failed: " + e.getMessage());
        }
    }
}
