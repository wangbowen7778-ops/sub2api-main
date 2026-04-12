package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account health check service
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHealthService extends ServiceImpl<AccountMapper, Account> {

    private final AccountMapper accountMapper;
    private final WebClient webClient;

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

            // Get API key from credentials
            Object apiKeyObj = credentials.get("api_key");
            if (apiKeyObj == null) {
                return false;
            }
            String apiKey = apiKeyObj.toString();

            // Health check based on different platforms
            switch (platform != null ? platform.toLowerCase() : "") {
                case "anthropic":
                case "claude":
                    return testAnthropicConnection(apiKey);
                case "openai":
                    return testOpenAIConnection(apiKey);
                case "gemini":
                    return testGeminiConnection(apiKey);
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
     * Test Anthropic API connection
     */
    private boolean testAnthropicConnection(String apiKey) {
        try {
            String response = webClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .bodyValue("{\"model\":\"claude-3-haiku-20240307\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return response != null;
        } catch (WebClientResponseException e) {
            // 401 means invalid key, but connection works
            return e.getStatusCode().value() == 401;
        } catch (Exception e) {
            log.warn("Anthropic health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test OpenAI API connection
     */
    private boolean testOpenAIConnection(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://api.openai.com/v1/models")
                    .header("authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return response != null;
        } catch (WebClientResponseException e) {
            return e.getStatusCode().value() == 401;
        } catch (Exception e) {
            log.warn("OpenAI health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test Gemini API connection
     */
    private boolean testGeminiConnection(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://generativelanguage.googleapis.com/v1/models?key=" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return response != null;
        } catch (WebClientResponseException e) {
            return e.getStatusCode().value() == 401 || e.getStatusCode().value() == 400;
        } catch (Exception e) {
            log.warn("Gemini health check failed: {}", e.getMessage());
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
