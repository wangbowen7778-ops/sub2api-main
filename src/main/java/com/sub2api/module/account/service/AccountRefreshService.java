package com.sub2api.module.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account credential refresh service
 * Note: Go backend stores refresh token in credentials JSONB, not as separate field
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRefreshService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccountRefreshService.class);

    private final AccountService accountService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Refresh OAuth credentials (scheduled task)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional(rollbackFor = Exception.class)
    public void refreshOAuthCredentials() {
        log.debug("Starting OAuth credential refresh...");

        List<Account> accounts = accountService.listAllOAuthAccounts();
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
        Map<String, Object> credentials = account.getCredentials();
        if (credentials == null) {
            log.warn("Account has no credentials, skipping refresh: accountId={}", account.getId());
            return;
        }

        // Get refresh token from credentials JSON
        Object refreshTokenObj = credentials.get("refresh_token");
        String refreshToken = refreshTokenObj != null ? refreshTokenObj.toString() : null;

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Account has no refresh token in credentials, skipping refresh: accountId={}, platform={}", account.getId(), account.getPlatform());
            return;
        }

        try {
            Map<String, Object> newCredentials = null;

            String platform = account.getPlatform();
            switch (platform != null ? platform.toLowerCase() : "") {
                case "anthropic":
                    newCredentials = refreshAnthropicToken(account, refreshToken);
                    break;
                case "openai":
                    newCredentials = refreshOpenAIToken(account, refreshToken);
                    break;
                case "google":
                    newCredentials = refreshGoogleToken(account, refreshToken);
                    break;
                case "linuxdo":
                    newCredentials = refreshLinuxDoToken(account, refreshToken);
                    break;
                default:
                    log.warn("Unsupported platform for token refresh: accountId={}, platform={}", account.getId(), platform);
                    return;
            }

            if (newCredentials != null) {
                // Update account credentials
                account.setCredentials(newCredentials);
                account.setStatus(AccountStatus.ACTIVE.getValue());
                account.setErrorMessage(null);
                accountService.updateById(account);
                log.info("Successfully refreshed token: accountId={}, platform={}", account.getId(), platform);
            }

        } catch (Exception e) {
            log.error("Failed to refresh account credential: accountId={}, error={}", account.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Refresh Anthropic OAuth token
     * 端点: https://platform.claude.com/v1/oauth/token
     */
    private Map<String, Object> refreshAnthropicToken(Account account, String refreshToken) {
        try {
            Object clientId = account.getCredentials().get("client_id");
            if (clientId == null) {
                log.warn("Anthropic token refresh missing client_id: accountId={}", account.getId());
                return null;
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("grant_type", "refresh_token");
            requestBody.put("refresh_token", refreshToken);
            requestBody.put("client_id", clientId.toString());

            // 调用 Anthropic token 刷新端点
            String response = webClient.post()
                    .uri("https://platform.claude.com/v1/oauth/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                Map<String, Object> credentials = new HashMap<>(account.getCredentials());
                credentials.put("access_token", json.get("access_token").asText());
                if (json.has("refresh_token")) {
                    credentials.put("refresh_token", json.get("refresh_token").asText());
                }
                if (json.has("token_type")) {
                    credentials.put("token_type", json.get("token_type").asText());
                }
                if (json.has("scope")) {
                    credentials.put("scope", json.get("scope").asText());
                }
                log.info("Anthropic token refreshed successfully: accountId={}", account.getId());
                return credentials;
            }
            return null;
        } catch (Exception e) {
            log.error("Anthropic token refresh failed: accountId={}, error={}", account.getId(), e.getMessage());
            throw new RuntimeException("Anthropic token refresh failed", e);
        }
    }

    /**
     * Refresh OpenAI OAuth token
     * 端点: https://auth.openai.com/oauth/token
     * 使用 form data 而不是 JSON
     */
    private Map<String, Object> refreshOpenAIToken(Account account, String refreshToken) {
        try {
            Object clientId = account.getCredentials().get("client_id");
            if (clientId == null) {
                log.warn("OpenAI token refresh missing client_id: accountId={}", account.getId());
                return null;
            }

            // OpenAI 使用 form data 格式
            String response = webClient.post()
                    .uri("https://auth.openai.com/oauth/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(String.format(
                            "grant_type=refresh_token&refresh_token=%s&client_id=%s&scope=openai%20api",
                            java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8),
                            java.net.URLEncoder.encode(clientId.toString(), java.nio.charset.StandardCharsets.UTF_8)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                Map<String, Object> credentials = new HashMap<>(account.getCredentials());
                credentials.put("access_token", json.get("access_token").asText());
                if (json.has("refresh_token") && !json.get("refresh_token").isNull()) {
                    credentials.put("refresh_token", json.get("refresh_token").asText());
                }
                if (json.has("token_type")) {
                    credentials.put("token_type", json.get("token_type").asText());
                }
                if (json.has("scope")) {
                    credentials.put("scope", json.get("scope").asText());
                }
                log.info("OpenAI token refreshed successfully: accountId={}", account.getId());
                return credentials;
            }
            return null;
        } catch (Exception e) {
            log.error("OpenAI token refresh failed: accountId={}, error={}", account.getId(), e.getMessage());
            throw new RuntimeException("OpenAI token refresh failed", e);
        }
    }

    /**
     * Refresh Google OAuth token
     */
    private Map<String, Object> refreshGoogleToken(Account account, String refreshToken) {
        try {
            Object clientId = account.getCredentials().get("client_id");
            Object clientSecret = account.getCredentials().get("client_secret");

            if (clientId == null || clientSecret == null) {
                log.warn("Google token refresh missing client credentials: accountId={}", account.getId());
                return null;
            }

            // Call Google token endpoint
            String response = webClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(String.format(
                            "grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                            clientId, clientSecret, refreshToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                Map<String, Object> credentials = new HashMap<>(account.getCredentials());
                credentials.put("access_token", json.get("access_token").asText());
                if (json.has("refresh_token")) {
                    credentials.put("refresh_token", json.get("refresh_token").asText());
                }
                return credentials;
            }
            return null;
        } catch (Exception e) {
            log.error("Google token refresh failed: accountId={}, error={}", account.getId(), e.getMessage());
            throw new RuntimeException("Google token refresh failed", e);
        }
    }

    /**
     * Refresh Linux.do OAuth token
     */
    private Map<String, Object> refreshLinuxDoToken(Account account, String refreshToken) {
        try {
            Object clientId = account.getCredentials().get("client_id");
            Object clientSecret = account.getCredentials().get("client_secret");

            if (clientId == null || clientSecret == null) {
                log.warn("Linux.do token refresh missing client credentials: accountId={}", account.getId());
                return null;
            }

            // Call Linux.do token endpoint
            String response = webClient.post()
                    .uri("https://connect.linux.do/oauth/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(String.format(
                            "grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                            clientId, clientSecret, refreshToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                Map<String, Object> credentials = new HashMap<>(account.getCredentials());
                credentials.put("access_token", json.get("access_token").asText());
                if (json.has("refresh_token")) {
                    credentials.put("refresh_token", json.get("refresh_token").asText());
                }
                return credentials;
            }
            return null;
        } catch (Exception e) {
            log.error("Linux.do token refresh failed: accountId={}, error={}", account.getId(), e.getMessage());
            throw new RuntimeException("Linux.do token refresh failed", e);
        }
    }

    /**
     * Handle refresh failure
     */
    private void handleRefreshFailure(Account account, String error) {
        account.setErrorMessage(error);
        account.setStatus(AccountStatus.ERROR.getValue());
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
