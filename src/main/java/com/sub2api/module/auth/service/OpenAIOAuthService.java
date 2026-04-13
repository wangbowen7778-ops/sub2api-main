package com.sub2api.module.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI OAuth Service
 * OpenAI OAuth 认证服务，处理 OpenAI 账号的 OAuth 流程
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIOAuthService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // OAuth 配置
    private static final String OPENAI_AUTH_URL = "https://auth.openai.com/authorize";
    private static final String OPENAI_TOKEN_URL = "https://auth.openai.com/oauth/token";
    private static final String OPENAI_USERINFO_URL = "https://api.openai.com/v1/me";

    // 会话存储
    private final Map<String, OAuthSession> sessionStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    // 缓存 key 前缀
    private static final String TOKEN_CACHE_PREFIX = "openai:oauth:token:";

    /**
     * OAuth 会话
     */
    @Data
    public static class OAuthSession {
        private String state;
        private String codeVerifier;
        private String redirectUri;
        private String proxyUrl;
        private long createdAt;
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
        private String scope;
        private String email;
        private String openaiUserId;
    }

    /**
     * 生成授权 URL
     */
    public String generateAuthURL(String redirectUri, String proxyUrl) {
        String state = generateState();
        String codeVerifier = generateCodeVerifier();
        String sessionId = generateSessionId();

        OAuthSession session = new OAuthSession();
        session.setState(state);
        session.setCodeVerifier(codeVerifier);
        session.setRedirectUri(redirectUri);
        session.setProxyUrl(proxyUrl);
        session.setCreatedAt(System.currentTimeMillis());

        sessionStore.put(sessionId, session);

        String codeChallenge = generateCodeChallenge(codeVerifier);

        StringBuilder url = new StringBuilder(OPENAI_AUTH_URL);
        url.append("?client_id=").append(getClientId());
        url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        url.append("&response_type=code");
        url.append("&scope=openai+api");
        url.append("&state=").append(state);
        url.append("&code_challenge=").append(codeChallenge);
        url.append("&code_challenge_method=S256");

        log.info("Generated OpenAI OAuth URL: sessionId={}", sessionId);
        return url.toString();
    }

    /**
     * 交换 Code 获取 Token
     */
    public TokenInfo exchangeCode(String sessionId, String code) {
        OAuthSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found or expired");
        }

        try {
            String tokenUrl = session.getProxyUrl() != null && !session.getProxyUrl().isBlank()
                    ? session.getProxyUrl() + "/oauth/token"
                    : OPENAI_TOKEN_URL;

            String bodyStr = String.format(
                    "grant_type=authorization_code&code=%s&code_verifier=%s&client_id=%s&redirect_uri=%s",
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(session.getCodeVerifier(), StandardCharsets.UTF_8),
                    URLEncoder.encode(getClientId(), StandardCharsets.UTF_8),
                    URLEncoder.encode(session.getRedirectUri(), StandardCharsets.UTF_8)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> entity = new HttpEntity<>(bodyStr, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());

                TokenInfo tokenInfo = new TokenInfo();
                tokenInfo.setAccessToken(json.get("access_token").asText());
                tokenInfo.setRefreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : null);
                tokenInfo.setExpiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 3600);
                tokenInfo.setExpiresAt(Instant.now().getEpochSecond() + tokenInfo.getExpiresIn() - 300);
                tokenInfo.setTokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer");
                tokenInfo.setScope(json.has("scope") ? json.get("scope").asText() : null);

                sessionStore.remove(sessionId);
                fetchUserInfo(tokenInfo);
                cacheToken(tokenInfo);

                log.info("OpenAI OAuth token exchanged: email={}", tokenInfo.getEmail());
                return tokenInfo;
            }

            throw new RuntimeException("Token exchange failed: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("OpenAI token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * 刷新 Token
     */
    public TokenInfo refreshToken(String refreshToken, String proxyUrl) {
        try {
            String tokenUrl = proxyUrl != null && !proxyUrl.isBlank()
                    ? proxyUrl + "/oauth/token"
                    : OPENAI_TOKEN_URL;

            String bodyStr = String.format(
                    "grant_type=refresh_token&refresh_token=%s&client_id=%s",
                    URLEncoder.encode(refreshToken, StandardCharsets.UTF_8),
                    URLEncoder.encode(getClientId(), StandardCharsets.UTF_8)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> entity = new HttpEntity<>(bodyStr, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());

                TokenInfo tokenInfo = new TokenInfo();
                tokenInfo.setAccessToken(json.get("access_token").asText());
                tokenInfo.setRefreshToken(json.has("refresh_token") ? json.get("refresh_token").asText() : refreshToken);
                tokenInfo.setExpiresIn(json.has("expires_in") ? json.get("expires_in").asLong() : 3600);
                tokenInfo.setExpiresAt(Instant.now().getEpochSecond() + tokenInfo.getExpiresIn() - 300);
                tokenInfo.setTokenType(json.has("token_type") ? json.get("token_type").asText() : "Bearer");

                fetchUserInfo(tokenInfo);
                cacheToken(tokenInfo);

                log.info("OpenAI OAuth token refreshed: email={}", tokenInfo.getEmail());
                return tokenInfo;
            }

            throw new RuntimeException("Token refresh failed: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("OpenAI token refresh failed: {}", e.getMessage());
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取用户信息
     */
    private void fetchUserInfo(TokenInfo tokenInfo) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + tokenInfo.getAccessToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_USERINFO_URL, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                tokenInfo.setEmail(json.has("email") ? json.get("email").asText() : null);
                tokenInfo.setOpenaiUserId(json.has("id") ? json.get("id").asText() : null);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch OpenAI user info: {}", e.getMessage());
        }
    }

    /**
     * 缓存 Token
     */
    private void cacheToken(TokenInfo tokenInfo) {
        if (tokenInfo.getOpenaiUserId() == null) {
            return;
        }
        String key = TOKEN_CACHE_PREFIX + tokenInfo.getOpenaiUserId();
        try {
            String json = objectMapper.writeValueAsString(tokenInfo);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(tokenInfo.getExpiresIn()));
        } catch (Exception e) {
            log.warn("Failed to cache OpenAI token: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的 Token
     */
    public TokenInfo getCachedToken(String openaiUserId) {
        String key = TOKEN_CACHE_PREFIX + openaiUserId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, TokenInfo.class);
            }
        } catch (Exception e) {
            log.warn("Failed to get cached OpenAI token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(TokenInfo tokenInfo) {
        if (tokenInfo == null || tokenInfo.getExpiresAt() == 0) {
            return true;
        }
        return Instant.now().getEpochSecond() >= tokenInfo.getExpiresAt();
    }

    /**
     * 使 Token 缓存失效
     */
    public void invalidateToken(String openaiUserId) {
        String key = TOKEN_CACHE_PREFIX + openaiUserId;
        redisTemplate.delete(key);
    }

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
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    private String getClientId() {
        return "YOUR_OPENAI_CLIENT_ID";
    }
}
