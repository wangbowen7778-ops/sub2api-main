package com.sub2api.module.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.Platform;
import com.sub2api.module.account.service.AccountService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Antigravity 平台服务
 * 处理 Antigravity 平台的 OAuth 认证、Token 管理和智能重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AntigravityService {

    private final AccountService accountService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // Antigravity API 配置
    private static final String ANTI_GRAVITY_AUTH_URL = "https://accounts.google.com/o/oauth2/auth";
    private static final String ANTI_GRAVITY_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String ANTI_GRAVITY_API_BASE = "https://api.antigravity.dev";

    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final long RETRY_MAX_DELAY_MS = 16000;

    // 限流阈值 (7秒)
    private static final Duration RATE_LIMIT_THRESHOLD = Duration.ofSeconds(7);
    private static final Duration DEFAULT_RATE_LIMIT_DURATION = Duration.ofSeconds(30);

    // MODEL_CAPACITY_EXHAUSTED 重试配置
    private static final int MODEL_CAPACITY_MAX_ATTEMPTS = 60;
    private static final Duration MODEL_CAPACITY_WAIT = Duration.ofSeconds(1);

    // 缓存 key 前缀
    private static final String TOKEN_CACHE_PREFIX = "antigravity:token:";
    private static final String RATE_LIMIT_CACHE_PREFIX = "antigravity:ratelimit:";
    private static final String MODEL_CAPACITY_COOLDOWN_PREFIX = "antigravity:model_capacity_cooldown:";

    // 会话存储 (内存中，生产环境可用 Redis)
    private final Map<String, OAuthSession> sessionStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    // 模型容量耗尽全局去重
    private final Map<String, OffsetDateTime> modelCapacityCooldowns = new ConcurrentHashMap<>();

    /**
     * OAuth 会话
     */
    @Data
    public static class OAuthSession {
        private String state;
        private String codeVerifier;
        private String proxyURL;
        private OffsetDateTime createdAt;
    }

    /**
     * Token 信息
     */
    @Data
    public static class TokenInfo {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private long expiresAt;
        private String tokenType;
        private String email;
        private String projectId;
        private boolean projectIdMissing;
        private String planType;
    }

    /**
     * 智能重试结果
     */
    @Data
    public static class SmartRetryResult {
        private boolean shouldRetry;
        private boolean shouldSwitchAccount;
        private boolean shouldRateLimitModel;
        private Duration waitDuration;
        private String modelName;
        private boolean isModelCapacityExhausted;
        private Object response; // 可以是 ResponseEntity 或 error
    }

    /**
     * 转发结果
     */
    @Data
    public static class ForwardResult {
        private int statusCode;
        private HttpHeaders headers;
        private String body;
        private boolean success;
        private String error;

        public ForwardResult() {}

        public ForwardResult(int statusCode, HttpHeaders headers, String body, boolean success, String error) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.success = success;
            this.error = error;
        }
    }

    /**
     * 账号切换错误
     */
    public static class AccountSwitchError extends RuntimeException {
        private final long originalAccountId;
        private final String rateLimitedModel;
        private final boolean isStickySession;

        public AccountSwitchError(long originalAccountId, String rateLimitedModel, boolean isStickySession) {
            super(String.format("account %d model %s rate limited, need switch", originalAccountId, rateLimitedModel));
            this.originalAccountId = originalAccountId;
            this.rateLimitedModel = rateLimitedModel;
            this.isStickySession = isStickySession;
        }

        public long getOriginalAccountId() { return originalAccountId; }
        public String getRateLimitedModel() { return rateLimitedModel; }
        public boolean isStickySession() { return isStickySession; }
    }

    @PostConstruct
    public void init() {
        log.info("AntigravityService initialized");
    }

    // ========== OAuth 相关方法 ==========

    /**
     * 生成 OAuth 授权 URL
     */
    public OAuthSession generateAuthURL(Long proxyId) {
        String state = generateState();
        String codeVerifier = generateCodeVerifier();
        String sessionId = generateSessionId();

        String proxyURL = "";
        if (proxyId != null) {
            proxyURL = getProxyURL(proxyId);
        }

        OAuthSession session = new OAuthSession();
        session.setState(state);
        session.setCodeVerifier(codeVerifier);
        session.setProxyURL(proxyURL);
        session.setCreatedAt(OffsetDateTime.now());

        sessionStore.put(sessionId, session);

        String codeChallenge = generateCodeChallenge(codeVerifier);
        String authURL = buildAuthorizationURL(state, codeChallenge);

        log.info("Generated OAuth session: sessionId={}, proxyURL={}", sessionId, proxyURL);
        return session;
    }

    /**
     * 获取授权 URL (兼容旧接口)
     */
    public String getAuthURL(Long proxyId) {
        OAuthSession session = generateAuthURL(proxyId);
        String codeChallenge = generateCodeChallenge(session.getCodeVerifier());
        return buildAuthorizationURL(session.getState(), codeChallenge);
    }

    /**
     * 交换 Code 获取 Token
     */
    public TokenInfo exchangeCode(String sessionId, String state, String code, Long proxyId) {
        OAuthSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found or expired");
        }

        if (state == null || !state.equals(session.getState())) {
            throw new RuntimeException("Invalid state");
        }

        String proxyURL = session.getProxyURL();
        if (proxyId != null) {
            proxyURL = getProxyURL(proxyId);
        }

        try {
            TokenResponse tokenResp = exchangeCodeForToken(code, session.getCodeVerifier(), proxyURL);

            // 删除 session
            sessionStore.remove(sessionId);

            // 计算过期时间（减去 5 分钟安全窗口）
            long expiresAt = Instant.now().getEpochSecond() + tokenResp.getExpiresIn() - 300;

            TokenInfo result = new TokenInfo();
            result.setAccessToken(tokenResp.getAccessToken());
            result.setRefreshToken(tokenResp.getRefreshToken());
            result.setExpiresIn(tokenResp.getExpiresIn());
            result.setExpiresAt(expiresAt);
            result.setTokenType(tokenResp.getTokenType());

            // 获取用户信息
            try {
                UserInfo userInfo = getUserInfo(tokenResp.getAccessToken(), proxyURL);
                if (userInfo != null) {
                    result.setEmail(userInfo.getEmail());
                }
            } catch (Exception e) {
                log.warn("Failed to get user info: {}", e.getMessage());
            }

            // 获取项目信息
            try {
                ProjectInfo projectInfo = loadProjectIdWithRetry(tokenResp.getAccessToken(), proxyURL, 3);
                if (projectInfo != null) {
                    result.setProjectId(projectInfo.getProjectId());
                    if (projectInfo.getSubscription() != null) {
                        result.setPlanType(projectInfo.getSubscription().getPlanType());
                    }
                } else {
                    result.setProjectIdMissing(true);
                }
            } catch (Exception e) {
                log.warn("Failed to get project ID: {}", e.getMessage());
                result.setProjectIdMissing(true);
            }

            // 缓存 token
            cacheToken(result);

            log.info("Token exchanged successfully: email={}, projectId={}", result.getEmail(), result.getProjectId());
            return result;

        } catch (Exception e) {
            log.error("Token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * 刷新 Token
     */
    public TokenInfo refreshToken(String refreshToken, String proxyURL) {
        for (int attempt = 0; attempt <= 3; attempt++) {
            if (attempt > 0) {
                long backoff = (1L << (attempt - 1)) * 1000; // 1s, 2s, 4s
                if (backoff > 30000) {
                    backoff = 30000;
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            try {
                TokenResponse tokenResp = refreshAccessToken(refreshToken, proxyURL);
                long expiresAt = Instant.now().getEpochSecond() + tokenResp.getExpiresIn() - 300;

                TokenInfo result = new TokenInfo();
                result.setAccessToken(tokenResp.getAccessToken());
                result.setRefreshToken(tokenResp.getRefreshToken());
                result.setExpiresIn(tokenResp.getExpiresIn());
                result.setExpiresAt(expiresAt);
                result.setTokenType(tokenResp.getTokenType());

                // 缓存
                cacheToken(result);

                log.info("Token refreshed successfully: expiresIn={}, expiresAt={}",
                        tokenResp.getExpiresIn(), expiresAt);
                return result;

            } catch (Exception e) {
                log.warn("Token refresh attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        throw new RuntimeException("Token refresh failed after 3 attempts");
    }

    // ========== Token 获取/管理 ==========

    /**
     * 获取账号的有效 Access Token
     */
    public String getValidAccessToken(Account account) {
        if (account == null) {
            throw new RuntimeException("Account is null");
        }

        Map<String, Object> credentials = account.getCredentials();
        if (credentials == null) {
            throw new RuntimeException("Account credentials is null");
        }

        // 先检查缓存
        String cachedToken = getCachedToken(account.getId());
        if (cachedToken != null && !isTokenExpired(cachedToken)) {
            return cachedToken;
        }

        // 获取 refresh token
        Object refreshTokenObj = credentials.get("refresh_token");
        String refreshToken = refreshTokenObj != null ? refreshTokenObj.toString() : null;

        if (refreshToken == null || refreshToken.isBlank()) {
            // 尝试使用现有的 access token
            Object accessTokenObj = credentials.get("access_token");
            if (accessTokenObj != null) {
                return accessTokenObj.toString();
            }
            throw new RuntimeException("No refresh token available");
        }

        // 获取 proxy URL
        String proxyURL = getProxyURL(account.getId());

        // 刷新 token
        try {
            TokenInfo tokenInfo = refreshToken(refreshToken, proxyURL);

            // 更新账号 credentials
            Map<String, Object> newCredentials = new HashMap<>(credentials);
            newCredentials.put("access_token", tokenInfo.getAccessToken());
            if (tokenInfo.getRefreshToken() != null) {
                newCredentials.put("refresh_token", tokenInfo.getRefreshToken());
            }
            account.setCredentials(newCredentials);
            accountService.updateById(account);

            return tokenInfo.getAccessToken();

        } catch (Exception e) {
            log.error("Failed to refresh token for account {}: {}", account.getId(), e.getMessage());
            throw new RuntimeException("Failed to get valid access token: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 Token 是否即将过期
     */
    public boolean isTokenExpired(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return true;
        }

        // 从缓存获取过期时间
        String key = TOKEN_CACHE_PREFIX + hashToken(accessToken);
        String expiresAtStr = redisTemplate.opsForValue().get(key + ":expires");
        if (expiresAtStr == null) {
            // 没有过期时间信息，假设有效
            return false;
        }

        try {
            long expiresAt = Long.parseLong(expiresAtStr);
            return Instant.now().getEpochSecond() >= expiresAt - 300; // 5分钟安全窗口
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ========== 转发请求 ==========

    /**
     * 转发请求 (带智能重试)
     */
    public ForwardResult forwardRequest(Account account, String action, Map<String, Object> body, boolean isStickySession) {
        String accessToken;
        try {
            accessToken = getValidAccessToken(account);
        } catch (Exception e) {
            return new ForwardResult(0, null, null, false, "Failed to get access token: " + e.getMessage());
        }

        String proxyURL = getProxyURL(account.getId());
        String baseURL = proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL;

        String url = baseURL + action;
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + accessToken);

                String jsonBody = objectMapper.writeValueAsString(body);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);

                ForwardResult result = new ForwardResult();
                result.setStatusCode(response.getStatusCode().value());
                result.setHeaders(response.getHeaders());
                result.setBody(response.getBody());
                result.setSuccess(true);
                return result;

            } catch (HttpClientErrorException e) {
                // 4xx 错误，不重试
                ForwardResult result = new ForwardResult();
                result.setStatusCode(e.getStatusCode().value());
                result.setBody(e.getResponseBodyAsString());
                result.setSuccess(false);
                result.setError("Client error: " + e.getMessage());
                return result;

            } catch (Exception e) {
                if (attempt >= MAX_RETRIES) {
                    ForwardResult result = new ForwardResult();
                    result.setStatusCode(503);
                    result.setSuccess(false);
                    result.setError("Max retries exceeded: " + e.getMessage());
                    return result;
                }

                // 计算退避延迟
                long delay = Math.min(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)), RETRY_MAX_DELAY_MS);
                log.warn("Request failed, retrying in {}ms: attempt={}, error={}", delay, attempt, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        ForwardResult result = new ForwardResult();
        result.setStatusCode(503);
        result.setSuccess(false);
        result.setError("Max retries exceeded");
        return result;
    }

    /**
     * 转发请求 (简化版)
     */
    public String forward(String action, Map<String, Object> body, String accessToken, String proxyURL) {
        String baseURL = proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL;
        String url = baseURL + action;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            return response.getBody();

        } catch (Exception e) {
            log.error("Forward request failed: {}", e.getMessage());
            throw new RuntimeException("Forward request failed: " + e.getMessage(), e);
        }
    }

    // ========== 限流处理 ==========

    /**
     * 检查是否触发智能重试
     */
    public SmartRetryResult checkSmartRetry(Account account, int statusCode, String responseBody, String requestedModel) {
        SmartRetryResult result = new SmartRetryResult();
        result.setShouldRetry(false);
        result.setShouldSwitchAccount(false);
        result.setShouldRateLimitModel(false);

        if (statusCode != 429 && statusCode != 503) {
            return result;
        }

        // 解析 retry info
        RetryInfo retryInfo = parseRetryInfo(responseBody);

        if (retryInfo == null) {
            // 没有 retry info，使用默认行为
            if (statusCode == 503) {
                result.setShouldRetry(true);
                result.setWaitDuration(DEFAULT_RATE_LIMIT_DURATION);
            }
            return result;
        }

        Duration retryDelay = Duration.ofSeconds(retryInfo.getRetryDelay());

        // 判断是否需要限流模型并切换账号
        if (retryDelay.compareTo(RATE_LIMIT_THRESHOLD) >= 0) {
            result.setShouldSwitchAccount(true);
            result.setShouldRateLimitModel(true);
            result.setWaitDuration(retryDelay);
            result.setModelName(retryInfo.getModelName());

            if ("MODEL_CAPACITY_EXHAUSTED".equals(retryInfo.getReason())) {
                result.setModelCapacityExhausted(true);
            }

            return result;
        }

        // retryDelay < 阈值，智能重试
        result.setShouldRetry(true);
        result.setWaitDuration(retryDelay);
        result.setModelName(retryInfo.getModelName());

        if ("MODEL_CAPACITY_EXHAUSTED".equals(retryInfo.getReason())) {
            result.setModelCapacityExhausted(true);
        }

        return result;
    }

    /**
     * 设置模型限流
     */
    public void setModelRateLimit(Account account, String modelName, Duration rateLimitDuration) {
        String key = RATE_LIMIT_CACHE_PREFIX + account.getId() + ":" + modelName;
        redisTemplate.opsForValue().set(key, String.valueOf(Instant.now().plus(rateLimitDuration).getEpochSecond()));
        redisTemplate.expire(key, rateLimitDuration.plus(Duration.ofMinutes(5)));

        log.info("Set model rate limit: accountId={}, model={}, duration={}", account.getId(), modelName, rateLimitDuration);
    }

    /**
     * 获取模型限流剩余时间
     */
    public Duration getModelRateLimitRemaining(Account account, String modelName) {
        String key = RATE_LIMIT_CACHE_PREFIX + account.getId() + ":" + modelName;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Duration.ZERO;
        }

        try {
            long expiresAt = Long.parseLong(value);
            long remaining = expiresAt - Instant.now().getEpochSecond();
            if (remaining <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofSeconds(remaining);
        } catch (NumberFormatException e) {
            return Duration.ZERO;
        }
    }

    // ========== 辅助方法 ==========

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    private String buildAuthorizationURL(String state, String codeChallenge) {
        return ANTI_GRAVITY_AUTH_URL
                + "?client_id=YOUR_CLIENT_ID" // 需要配置
                + "&redirect_uri=YOUR_REDIRECT_URI" // 需要配置
                + "&response_type=code"
                + "&scope=https://www.googleapis.com/auth/cloud-platform"
                + "&state=" + state
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";
    }

    private String getProxyURL(Long accountId) {
        // 实际实现应该从账号或代理配置中获取
        return "";
    }

    private String getProxyURL(Account account) {
        if (account.getExtra() != null) {
            Object proxyUrl = account.getExtra().get("proxy_url");
            if (proxyUrl != null) {
                return proxyUrl.toString();
            }
        }
        return "";
    }

    private void cacheToken(TokenInfo tokenInfo) {
        String key = TOKEN_CACHE_PREFIX + "account:" + hashToken(tokenInfo.getAccessToken());
        redisTemplate.opsForValue().set(key + ":token", tokenInfo.getAccessToken());
        redisTemplate.opsForValue().set(key + ":expires", String.valueOf(tokenInfo.getExpiresAt()));
        redisTemplate.expire(key, Duration.ofSeconds(tokenInfo.getExpiresIn()));
    }

    private String getCachedToken(Long accountId) {
        String pattern = TOKEN_CACHE_PREFIX + "account:*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            String key = keys.iterator().next();
            return redisTemplate.opsForValue().get(key + ":token");
        }
        return null;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    // ========== API 调用 ==========

    private TokenResponse exchangeCodeForToken(String code, String codeVerifier, String proxyURL) {
        String tokenURL = proxyURL.isBlank() ? ANTI_GRAVITY_TOKEN_URL : proxyURL + "/oauth/token";

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("code_verifier", codeVerifier);
        params.put("client_id", "YOUR_CLIENT_ID"); // 需要配置
        params.put("redirect_uri", "YOUR_REDIRECT_URI"); // 需要配置

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                tokenURL, HttpMethod.POST, entity, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            TokenResponse tokenResp = new TokenResponse();
            tokenResp.setAccessToken(json.get("access_token").asText());
            tokenResp.setRefreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null);
            tokenResp.setExpiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 3600);
            tokenResp.setTokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer");
            return tokenResp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response: " + e.getMessage(), e);
        }
    }

    private TokenResponse refreshAccessToken(String refreshToken, String proxyURL) {
        String tokenURL = proxyURL.isBlank() ? ANTI_GRAVITY_TOKEN_URL : proxyURL + "/oauth/token";

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        params.put("client_id", "YOUR_CLIENT_ID"); // 需要配置

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                tokenURL, HttpMethod.POST, entity, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            TokenResponse tokenResp = new TokenResponse();
            tokenResp.setAccessToken(json.get("access_token").asText());
            tokenResp.setRefreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null);
            tokenResp.setExpiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 3600);
            tokenResp.setTokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer");
            return tokenResp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response: " + e.getMessage(), e);
        }
    }

    private UserInfo getUserInfo(String accessToken, String proxyURL) {
        String url = (proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL) + "/v1/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            UserInfo userInfo = new UserInfo();
            userInfo.setEmail(json.has("email") ? json.get("email").asText() : null);
            return userInfo;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse user info: " + e.getMessage(), e);
        }
    }

    private ProjectInfo loadProjectIdWithRetry(String accessToken, String proxyURL, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                return getProjectInfo(accessToken, proxyURL);
            } catch (Exception e) {
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return null;
    }

    private ProjectInfo getProjectInfo(String accessToken, String proxyURL) {
        String url = (proxyURL.isBlank() ? ANTI_GRAVITY_API_BASE : proxyURL) + "/v1/projects";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            ProjectInfo projectInfo = new ProjectInfo();
            if (json.has("projects") && json.get("projects").isArray() && json.get("projects").size() > 0) {
                JsonNode project = json.get("projects").get(0);
                projectInfo.setProjectId(project.has("project_id") ? project.get("project_id").asText() : null);
            }
            return projectInfo;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse project info: " + e.getMessage(), e);
        }
    }

    private RetryInfo parseRetryInfo(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);

            // 检查 error 字段
            if (!json.has("error")) {
                return null;
            }

            JsonNode error = json.get("error");

            // 尝试从 errorDescription 或 meta 解析 retryDelay
            long retryDelay = 0;
            String reason = "";
            String modelName = "";

            if (error.has("error_description")) {
                String desc = error.get("error_description").asText();
                // 尝试解析 "Try again in Xs" 格式
                Pattern pattern = Pattern.compile("Try again in (\\d+)s");
                Matcher matcher = pattern.matcher(desc);
                if (matcher.find()) {
                    retryDelay = Long.parseLong(matcher.group(1));
                }
            }

            if (error.has("meta")) {
                JsonNode meta = error.get("meta");
                if (meta.has("retry_delay")) {
                    retryDelay = meta.get("retry_delay").asLong();
                }
                if (meta.has("reason")) {
                    reason = meta.get("reason").asText();
                }
                if (meta.has("model")) {
                    modelName = meta.get("model").asText();
                }
            }

            if (retryDelay > 0) {
                RetryInfo info = new RetryInfo();
                info.setRetryDelay(retryDelay);
                info.setReason(reason);
                info.setModelName(modelName);
                return info;
            }

        } catch (Exception e) {
            log.debug("Failed to parse retry info: {}", e.getMessage());
        }

        return null;
    }

    // ========== 内部类 ==========

    @Data
    private static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private String tokenType;
    }

    @Data
    private static class UserInfo {
        private String email;
    }

    @Data
    private static class ProjectInfo {
        private String projectId;
        private Subscription subscription;
    }

    @Data
    private static class Subscription {
        private String planType;
    }

    @Data
    private static class RetryInfo {
        private long retryDelay;
        private String reason;
        private String modelName;
    }
}