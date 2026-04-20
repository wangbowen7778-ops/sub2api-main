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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account health check service
 * 账号健康检查服务，支持定时检查和手动触发
 * 支持平台: Anthropic, OpenAI, Gemini, Antigravity
 * 支持账号类型: api_key, oauth
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHealthService extends ServiceImpl<AccountMapper, Account> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccountHealthService.class);

    private final AccountMapper accountMapper;
    private final WebClient webClient;

    /**
     * 默认测试模型
     */
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-3-haiku-20240307";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";
    private static final String DEFAULT_ANTIGRAVITY_MODEL = "claude-sonnet-4-20250514";

    /**
     * 定时健康检查 - 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional(rollbackFor = Exception.class)
    public void scheduledHealthCheck() {
        log.debug("Starting scheduled account health check...");

        // 检查所有非删除状态的活跃账号
        List<Account> accounts = list(new LambdaQueryWrapper<Account>()
                .eq(Account::getStatus, AccountStatus.ACTIVE.getValue())
                .isNull(Account::getDeletedAt));

        for (Account account : accounts) {
            checkAccountHealth(account);
        }

        // 同时检查 ERROR 状态的账号，尝试恢复
        List<Account> errorAccounts = list(new LambdaQueryWrapper<Account>()
                .eq(Account::getStatus, AccountStatus.ERROR.getValue())
                .isNull(Account::getDeletedAt));

        for (Account account : errorAccounts) {
            checkAccountHealth(account);
        }

        log.debug("Scheduled health check completed. Checked {} active accounts, {} error accounts",
                accounts.size(), errorAccounts.size());
    }

    /**
     * 检查单个账号的健康状态
     */
    private void checkAccountHealth(Account account) {
        try {
            // 检查是否过期
            if (account.getExpiresAt() != null && account.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
                if (Boolean.TRUE.equals(account.getAutoPauseOnExpired())) {
                    updateAccountStatus(account, AccountStatus.DISABLED, "Account expired");
                    return;
                }
            }

            // 调用平台 API 进行实际健康检查
            HealthCheckResult result = testAccountConnectionInternal(account);

            if (result.isHealthy()) {
                // 健康检查成功
                if (AccountStatus.ERROR.getValue().equals(account.getStatus()) ||
                        AccountStatus.CREDENTIAL_EXPIRED.getValue().equals(account.getStatus())) {
                    updateAccountStatus(account, AccountStatus.ACTIVE, "Health check passed - recovered");
                } else if (AccountStatus.EXHAUSTED.getValue().equals(account.getStatus())) {
                    updateAccountStatus(account, AccountStatus.ACTIVE, "Recovered");
                }
                // 清除之前的错误信息
                if (account.getErrorMessage() != null && !account.getErrorMessage().isEmpty()) {
                    account.setErrorMessage(null);
                    account.setUpdatedAt(java.time.OffsetDateTime.now());
                    accountMapper.updateById(account);
                }
            } else {
                // 健康检查失败
                updateAccountStatus(account, AccountStatus.ERROR, result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Account health check exception: accountId={}, error={}", account.getId(), e.getMessage());
            updateAccountStatus(account, AccountStatus.ERROR, e.getMessage());
        }
    }

    /**
     * 内部健康检查实现
     */
    private HealthCheckResult testAccountConnectionInternal(Account account) {
        try {
            String platform = account.getPlatform();
            String accountType = account.getType();
            Map<String, Object> credentials = account.getCredentials();

            if (credentials == null || credentials.isEmpty()) {
                return HealthCheckResult.unhealthy("No credentials available");
            }

            // 根据账号类型决定如何获取 token
            String token = getAuthToken(account, credentials, accountType);
            if (token == null || token.isEmpty()) {
                return HealthCheckResult.unhealthy("No valid auth token available");
            }

            // 根据平台进行健康检查
            return switch (platform != null ? platform.toLowerCase() : "") {
                case "anthropic", "claude" -> testAnthropicConnection(token, accountType);
                case "openai" -> testOpenAIConnection(token, accountType);
                case "gemini", "google" -> testGeminiConnection(token);
                case "antigravity" -> testAntigravityConnection(token, accountType);
                default -> HealthCheckResult.unhealthy("Unsupported platform: " + platform);
            };
        } catch (Exception e) {
            log.error("Connection test exception: accountId={}, error={}", account.getId(), e.getMessage());
            return HealthCheckResult.unhealthy("Connection test failed: " + e.getMessage());
        }
    }

    /**
     * 根据账号类型获取认证 token
     */
    private String getAuthToken(Account account, Map<String, Object> credentials, String accountType) {
        // OAuth 账号使用 access_token
        if ("oauth".equalsIgnoreCase(accountType) || "setup_token".equalsIgnoreCase(accountType)) {
            Object accessToken = credentials.get("access_token");
            return accessToken != null ? accessToken.toString() : null;
        }
        // API Key 账号使用 api_key
        Object apiKey = credentials.get("api_key");
        return apiKey != null ? apiKey.toString() : null;
    }

    /**
     * 测试 Anthropic API 连接
     * 支持 API Key 和 OAuth token
     */
    private HealthCheckResult testAnthropicConnection(String token, String accountType) {
        try {
            boolean isOAuth = "oauth".equalsIgnoreCase(accountType) || "setup_token".equalsIgnoreCase(accountType);

            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json");

            // 根据账号类型选择认证方式
            if (isOAuth) {
                requestSpec.header("Authorization", "Bearer " + token);
            } else {
                requestSpec.header("x-api-key", token);
            }

            String response = requestSpec
                    .bodyValue("{\"model\":\"" + DEFAULT_ANTHROPIC_MODEL + "\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                return HealthCheckResult.healthy();
            }
            return HealthCheckResult.unhealthy("Empty response from Anthropic API");
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                return HealthCheckResult.unhealthy("Invalid API key or access token");
            } else if (status == 429) {
                return HealthCheckResult.unhealthy("Rate limited");
            } else if (status >= 500) {
                return HealthCheckResult.unhealthy("Anthropic server error: " + status);
            }
            return HealthCheckResult.unhealthy("HTTP error: " + status);
        } catch (Exception e) {
            log.warn("Anthropic health check failed: {}", e.getMessage());
            return HealthCheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * 测试 OpenAI API 连接
     * 支持 API Key 和 OAuth token
     */
    private HealthCheckResult testOpenAIConnection(String token, String accountType) {
        try {
            boolean isOAuth = "oauth".equalsIgnoreCase(accountType);

            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri("https://api.openai.com/v1/models")
                    .header("Authorization", "Bearer " + token);

            String response = requestSpec
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.contains("data")) {
                return HealthCheckResult.healthy();
            }
            return HealthCheckResult.unhealthy("Invalid response from OpenAI API");
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                return HealthCheckResult.unhealthy("Invalid API key or access token");
            } else if (status == 429) {
                return HealthCheckResult.unhealthy("Rate limited");
            } else if (status >= 500) {
                return HealthCheckResult.unhealthy("OpenAI server error: " + status);
            }
            return HealthCheckResult.unhealthy("HTTP error: " + status);
        } catch (Exception e) {
            log.warn("OpenAI health check failed: {}", e.getMessage());
            return HealthCheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * 测试 Gemini API 连接
     */
    private HealthCheckResult testGeminiConnection(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://generativelanguage.googleapis.com/v1/models?key=" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.contains("models")) {
                return HealthCheckResult.healthy();
            }
            return HealthCheckResult.unhealthy("Invalid response from Gemini API");
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 401) {
                return HealthCheckResult.unhealthy("Invalid API key");
            } else if (status == 429) {
                return HealthCheckResult.unhealthy("Rate limited");
            } else if (status >= 500) {
                return HealthCheckResult.unhealthy("Google server error: " + status);
            }
            return HealthCheckResult.unhealthy("HTTP error: " + status);
        } catch (Exception e) {
            log.warn("Gemini health check failed: {}", e.getMessage());
            return HealthCheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * 测试 Antigravity API 连接
     * Antigravity 使用与 Anthropic 类似的 API 结构
     */
    private HealthCheckResult testAntigravityConnection(String token, String accountType) {
        try {
            boolean isOAuth = "oauth".equalsIgnoreCase(accountType);

            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri("https://api.antigravity.dev/v1/messages")
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json");

            if (isOAuth) {
                requestSpec.header("Authorization", "Bearer " + token);
            } else {
                requestSpec.header("x-api-key", token);
            }

            String response = requestSpec
                    .bodyValue("{\"model\":\"" + DEFAULT_ANTIGRAVITY_MODEL + "\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                return HealthCheckResult.healthy();
            }
            return HealthCheckResult.unhealthy("Empty response from Antigravity API");
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                return HealthCheckResult.unhealthy("Invalid credentials");
            } else if (status == 429) {
                return HealthCheckResult.unhealthy("Rate limited");
            } else if (status >= 500) {
                return HealthCheckResult.unhealthy("Antigravity server error: " + status);
            }
            return HealthCheckResult.unhealthy("HTTP error: " + status);
        } catch (Exception e) {
            log.warn("Antigravity health check failed: {}", e.getMessage());
            return HealthCheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * 更新账号状态
     */
    private void updateAccountStatus(Account account, AccountStatus status, String reason) {
        account.setStatus(status.getValue());
        account.setErrorMessage(reason);
        account.setUpdatedAt(java.time.OffsetDateTime.now());
        accountMapper.updateById(account);

        log.info("Account status changed: accountId={}, name={}, status={}, reason={}",
                account.getId(), account.getName(), status.getDescription(), reason);
    }

    /**
     * 手动测试账号连接
     */
    public HealthCheckResult testAccountConnection(Long accountId) {
        Account account = getById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        return testAccountConnectionInternal(account);
    }

    /**
     * 手动触发单个账号健康检查
     */
    @Transactional(rollbackFor = Exception.class)
    public void triggerHealthCheck(Long accountId) {
        Account account = getById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        checkAccountHealth(account);
    }

    /**
     * 手动触发所有账号健康检查
     */
    @Transactional(rollbackFor = Exception.class)
    public void triggerHealthCheckForAll() {
        List<Account> accounts = list(new LambdaQueryWrapper<Account>()
                .isNull(Account::getDeletedAt));

        for (Account account : accounts) {
            checkAccountHealth(account);
        }

        log.info("Manual health check triggered for {} accounts", accounts.size());
    }

    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private boolean healthy;
        private String errorMessage;

        public HealthCheckResult(boolean healthy, String errorMessage) {
            this.healthy = healthy;
            this.errorMessage = errorMessage;
        }

        public static HealthCheckResult healthy() {
            return new HealthCheckResult(true, null);
        }

        public static HealthCheckResult unhealthy(String errorMessage) {
            return new HealthCheckResult(false, errorMessage);
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
