package com.sub2api.module.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.auth.model.vo.GeminiAuthUrlVO;
import com.sub2api.module.auth.model.vo.GeminiOAuthCapabilities;
import com.sub2api.module.auth.model.vo.GeminiTokenInfo;
import com.sub2api.module.common.config.GeminiOAuthConfig;
import com.sub2api.module.proxy.service.ProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Gemini OAuth Service
 * <p>
 * Handles OAuth flow for Google Gemini accounts (Code Assist, Google One, AI Studio)
 * <p>
 * Ported from: backend/internal/service/gemini_oauth_service.go
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiOAuthService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiOAuthSessionStore sessionStore;
    private final ProxyService proxyService;

    @Value("${sub2api.gemini.oauth.client-id:#{null}}")
    private String configuredClientId;

    @Value("${sub2api.gemini.oauth.client-secret:#{null}}")
    private String configuredClientSecret;

    @Value("${sub2api.gemini.oauth.scopes:#{null}}")
    private String configuredScopes;

    // Tier constants
    public static final String TIER_GOOGLE_ONE_FREE = "google_one_free";
    public static final String TIER_GOOGLE_AI_PRO = "google_ai_pro";
    public static final String TIER_GOOGLE_AI_ULTRA = "google_ai_ultra";
    public static final String TIER_GCP_STANDARD = "gcp_standard";
    public static final String TIER_GCP_ENTERPRISE = "gcp_enterprise";
    public static final String TIER_AI_STUDIO_FREE = "aistudio_free";
    public static final String TIER_AI_STUDIO_PAID = "aistudio_paid";
    public static final String TIER_GOOGLE_ONE_UNKNOWN = "google_one_unknown";

    // Storage tier thresholds (bytes)
    private static final long TB = 1024L * 1024 * 1024 * 1024;
    private static final long STORAGE_TIER_UNLIMITED = 100 * TB;
    private static final long STORAGE_TIER_AI_PREMIUM = 2 * TB;
    private static final long STORAGE_TIER_STANDARD = 200L * 1024 * 1024 * 1024;
    private static final long STORAGE_TIER_BASIC = 100L * 1024 * 1024 * 1024;
    private static final long STORAGE_TIER_FREE = 15L * 1024 * 1024 * 1024;

    /**
     * Get OAuth capabilities
     */
    public GeminiOAuthCapabilities getOAuthConfig() {
        String clientId = getEffectiveClientId();
        String clientSecret = getEffectiveClientSecret();

        boolean enabled = clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && !clientId.equals(GeminiOAuthConfig.BUILT_IN_CLIENT_ID);

        return GeminiOAuthCapabilities.builder()
                .aiStudioOAuthEnabled(enabled)
                .requiredRedirectUris(new String[]{GeminiOAuthConfig.AI_STUDIO_REDIRECT_URI})
                .build();
    }

    /**
     * Generate authorization URL
     */
    public GeminiAuthUrlVO generateAuthUrl(Long proxyId, String redirectUri, String projectId, String oauthType, String tierId) {
        // Normalize OAuth type
        oauthType = (oauthType == null || oauthType.isBlank()) ? "code_assist" : oauthType.toLowerCase().trim();

        // Validate OAuth type
        if (!oauthType.equals("code_assist") && !oauthType.equals("google_one") && !oauthType.equals("ai_studio")) {
            throw new IllegalArgumentException("Invalid oauth_type: must be 'code_assist', 'google_one', or 'ai_studio'");
        }

        // Generate session data
        String sessionId = sessionStore.generateSessionId();
        String state = sessionStore.generateState();
        String codeVerifier = sessionStore.generateCodeVerifier();
        String codeChallenge = sessionStore.generateCodeChallenge(codeVerifier);

        // Get proxy URL if proxyId is provided
        String proxyUrl = null;
        if (proxyId != null) {
            proxyUrl = proxyService.getProxyUrlById(proxyId);
        }

        // Build OAuth session
        GeminiOAuthSessionStore.OAuthSession session = new GeminiOAuthSessionStore.OAuthSession();
        session.setState(state);
        session.setCodeVerifier(codeVerifier);
        session.setProxyUrl(proxyUrl);
        session.setProjectId(projectId != null ? projectId.trim() : null);
        session.setTierId(canonicalTierId(oauthType, tierId));
        session.setOauthType(oauthType);

        // Determine redirect URI based on OAuth type and client configuration
        String effectiveRedirectUri = determineRedirectUri(oauthType);
        session.setRedirectUri(effectiveRedirectUri);

        // Store session
        sessionStore.set(sessionId, session);

        // Build authorization URL
        OAuthConfig effectiveConfig = getEffectiveOAuthConfig(oauthType);
        String authUrl = buildAuthorizationUrl(effectiveConfig, state, codeChallenge, effectiveRedirectUri, projectId, oauthType);

        log.info("Generated Gemini OAuth URL: oauthType={}, sessionId={}", oauthType, sessionId);

        return GeminiAuthUrlVO.builder()
                .authUrl(authUrl)
                .sessionId(sessionId)
                .state(state)
                .build();
    }

    /**
     * Exchange authorization code for tokens
     */
    public GeminiTokenInfo exchangeCode(String sessionId, String state, String code, Long proxyId, String oauthType, String tierId) {
        // Validate session
        GeminiOAuthSessionStore.OAuthSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found or expired");
        }

        // Validate state
        if (state == null || state.isBlank() || !state.equals(session.getState())) {
            throw new IllegalArgumentException("Invalid state parameter");
        }

        // Normalize OAuth type
        oauthType = (oauthType == null || oauthType.isBlank()) ? "code_assist" : oauthType.toLowerCase().trim();

        // Get proxy URL
        String proxyUrl = session.getProxyUrl();
        if (proxyId != null && proxyUrl == null) {
            proxyUrl = proxyService.getProxyUrlById(proxyId);
        }

        // Determine redirect URI
        String redirectUri = determineRedirectUri(oauthType);

        // Delete session (single use)
        sessionStore.delete(sessionId);

        // Exchange code for tokens
        TokenResponse tokenResp = exchangeCodeForToken(code, session.getCodeVerifier(), redirectUri, proxyUrl);

        // Calculate expiration time with safety window
        long expiresAt = calculateExpiresAt(tokenResp.getExpiresIn());

        // Build token info
        GeminiTokenInfo tokenInfo = GeminiTokenInfo.builder()
                .accessToken(tokenResp.getAccessToken())
                .refreshToken(tokenResp.getRefreshToken())
                .tokenType(tokenResp.getTokenType() != null ? tokenResp.getRefreshToken() : "Bearer")
                .expiresIn(tokenResp.getExpiresIn())
                .expiresAt(expiresAt)
                .scope(tokenResp.getScope())
                .oauthType(oauthType)
                .build();

        // Handle based on OAuth type
        switch (oauthType) {
            case "code_assist":
                handleCodeAssist(tokenInfo, proxyUrl, session, tierId);
                break;
            case "google_one":
                handleGoogleOne(tokenInfo, proxyUrl, session, tierId);
                break;
            case "ai_studio":
                handleAiStudio(tokenInfo, session, tierId);
                break;
        }

        log.info("Exchanged code for token: oauthType={}, hasProjectId={}", oauthType, tokenInfo.getProjectId() != null);

        return tokenInfo;
    }

    /**
     * Refresh token with retry logic
     */
    public GeminiTokenInfo refreshToken(String oauthType, String refreshToken, String proxyUrl) {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                try {
                    long backoffMs = (1L << (attempt - 1)) * 1000; // 1s, 2s, 4s
                    if (backoffMs > 30000) {
                        backoffMs = 30000;
                    }
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Refresh interrupted", e);
                }
            }

            try {
                return doRefreshToken(oauthType, refreshToken, proxyUrl);
            } catch (Exception e) {
                lastException = e;
                if (isNonRetryableError(e)) {
                    break;
                }
                log.warn("Token refresh attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        throw new RuntimeException("Token refresh failed after " + maxRetries + " retries: " + lastException.getMessage(), lastException);
    }

    private GeminiTokenInfo doRefreshToken(String oauthType, String refreshToken, String proxyUrl) {
        OAuthConfig config = getEffectiveOAuthConfig(oauthType);

        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "refresh_token");
        formData.put("refresh_token", refreshToken);
        if (config.clientId != null && !config.clientId.isBlank()) {
            formData.put("client_id", config.clientId);
        }
        if (config.clientSecret != null && !config.clientSecret.isBlank()) {
            formData.put("client_secret", config.clientSecret);
        }

        String response = webClient.post()
                .uri(GeminiOAuthConfig.TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(buildFormData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofSeconds(30))
                .block();

        TokenResponse tokenResp = parseTokenResponse(response);

        long expiresAt = calculateExpiresAt(tokenResp.getExpiresIn());

        return GeminiTokenInfo.builder()
                .accessToken(tokenResp.getAccessToken())
                .refreshToken(tokenResp.getRefreshToken() != null ? tokenResp.getRefreshToken() : refreshToken)
                .tokenType(tokenResp.getTokenType() != null ? tokenResp.getTokenType() : "Bearer")
                .expiresIn(tokenResp.getExpiresIn())
                .expiresAt(expiresAt)
                .scope(tokenResp.getScope())
                .oauthType(oauthType)
                .build();
    }

    // ==================== Helper Methods ====================

    private String getEffectiveClientId() {
        if (configuredClientId != null && !configuredClientId.isBlank()) {
            return configuredClientId.trim();
        }
        return null;
    }

    private String getEffectiveClientSecret() {
        if (configuredClientSecret != null && !configuredClientSecret.isBlank()) {
            return configuredClientSecret.trim();
        }
        // Try environment variable
        String envSecret = System.getenv(GeminiOAuthConfig.BUILT_IN_CLIENT_SECRET_ENV);
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret.trim();
        }
        return null;
    }

    private String getEffectiveScopes(String oauthType) {
        if (configuredScopes != null && !configuredScopes.isBlank()) {
            return configuredScopes.replace(",", " ").trim();
        }
        return oauthType.equals("ai_studio")
                ? GeminiOAuthConfig.DEFAULT_AI_STUDIO_SCOPES
                : GeminiOAuthConfig.DEFAULT_CODE_ASSIST_SCOPES;
    }

    private OAuthConfig getEffectiveOAuthConfig(String oauthType) {
        String clientId = getEffectiveClientId();
        String clientSecret = getEffectiveClientSecret();

        if (clientId == null || clientSecret == null) {
            // Use built-in client
            clientId = GeminiOAuthConfig.BUILT_IN_CLIENT_ID;
            clientSecret = getEffectiveClientSecret();
            if (clientSecret == null) {
                throw new IllegalStateException("Gemini CLI OAuth client secret not configured. Set GEMINI_CLI_OAUTH_CLIENT_SECRET environment variable.");
            }
        }

        String scopes = getEffectiveScopes(oauthType);

        return new OAuthConfig(clientId, clientSecret, scopes);
    }

    private String determineRedirectUri(String oauthType) {
        OAuthConfig config = getEffectiveOAuthConfig(oauthType);
        boolean isBuiltIn = config.clientId.equals(GeminiOAuthConfig.BUILT_IN_CLIENT_ID);

        if (oauthType.equals("code_assist") || oauthType.equals("google_one")) {
            return GeminiOAuthConfig.GEMINI_CLI_REDIRECT_URI;
        } else {
            // ai_studio uses custom client or localhost
            return isBuiltIn ? GeminiOAuthConfig.AI_STUDIO_REDIRECT_URI : GeminiOAuthConfig.AI_STUDIO_REDIRECT_URI;
        }
    }

    private String buildAuthorizationUrl(OAuthConfig config, String state, String codeChallenge,
                                          String redirectUri, String projectId, String oauthType) {
        StringBuilder url = new StringBuilder(GeminiOAuthConfig.AUTHORIZE_URL);
        url.append("?response_type=code");
        url.append("&client_id=").append(urlEncode(config.clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&scope=").append(urlEncode(config.scopes));
        url.append("&state=").append(urlEncode(state));
        url.append("&code_challenge=").append(urlEncode(codeChallenge));
        url.append("&code_challenge_method=S256");
        url.append("&access_type=offline");
        url.append("&prompt=consent");
        url.append("&include_granted_scopes=true");

        if (projectId != null && !projectId.isBlank()) {
            url.append("&project_id=").append(urlEncode(projectId.trim()));
        }

        return url.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private TokenResponse exchangeCodeForToken(String code, String codeVerifier, String redirectUri, String proxyUrl) {
        OAuthConfig config = getEffectiveOAuthConfig("code_assist");

        Map<String, String> formData = new HashMap<>();
        formData.put("grant_type", "authorization_code");
        formData.put("code", code);
        formData.put("code_verifier", codeVerifier);
        formData.put("redirect_uri", redirectUri);
        formData.put("client_id", config.clientId);
        formData.put("client_secret", config.clientSecret);

        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri(GeminiOAuthConfig.TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);

        String response;
        try {
            response = requestSpec
                    .bodyValue(buildFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Token exchange failed: " + e.getResponseBodyAsString(), e);
        }

        return parseTokenResponse(response);
    }

    private TokenResponse parseTokenResponse(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);

            TokenResponse tokenResp = new TokenResponse();
            tokenResp.setAccessToken(json.get("access_token").asText());
            if (json.has("refresh_token") && !json.get("refresh_token").isNull()) {
                tokenResp.setRefreshToken(json.get("refresh_token").asText());
            }
            if (json.has("token_type")) {
                tokenResp.setTokenType(json.get("token_type").asText());
            }
            if (json.has("expires_in")) {
                tokenResp.setExpiresIn(json.get("expires_in").asLong());
            }
            if (json.has("scope")) {
                tokenResp.setScope(json.get("scope").asText());
            }

            return tokenResp;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response", e);
        }
    }

    private String buildFormData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!first) sb.append("&");
            first = false;
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private long calculateExpiresAt(long expiresIn) {
        // Subtract 5 minute safety window for network delay and clock skew
        // Also set minimum 30 seconds to prevent past time issues
        long safetyWindow = 300;
        long minTTL = 30;
        long expiresAt = Instant.now().getEpochSecond() + expiresIn - safetyWindow;
        long minExpiresAt = Instant.now().getEpochSecond() + minTTL;
        return Math.max(expiresAt, minExpiresAt);
    }

    private boolean isNonRetryableError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (
                msg.contains("invalid_grant") ||
                msg.contains("invalid_client") ||
                msg.contains("unauthorized_client") ||
                msg.contains("access_denied")
        );
    }

    private void handleCodeAssist(GeminiTokenInfo tokenInfo, String proxyUrl,
                                  GeminiOAuthSessionStore.OAuthSession session, String tierId) {
        // For Code Assist, we need to fetch project_id and tier_id via Code Assist API
        String projectId = session.getProjectId();
        String tier = tierId;

        if (projectId == null || projectId.isBlank()) {
            // Try to fetch project_id from Code Assist API
            CodeAssistInfo info = fetchCodeAssistInfo(tokenInfo.getAccessToken(), proxyUrl);
            if (info != null) {
                if (info.projectId != null && !info.projectId.isBlank()) {
                    projectId = info.projectId;
                }
                if (info.tierId != null && !info.tierId.isBlank()) {
                    tier = canonicalTierId("code_assist", info.tierId);
                }
            }
        }

        if (projectId == null || projectId.isBlank()) {
            throw new RuntimeException("Missing project_id for Code Assist OAuth. Please provide Project ID in the authorization form.");
        }

        tokenInfo.setProjectId(projectId);
        if (tier == null || tier.isBlank()) {
            tier = TIER_GCP_STANDARD;
        }
        tokenInfo.setTierId(canonicalTierId("code_assist", tier));
    }

    private void handleGoogleOne(GeminiTokenInfo tokenInfo, String proxyUrl,
                                  GeminiOAuthSessionStore.OAuthSession session, String tierId) {
        // For Google One, fetch tier from Drive API
        String tier = tierId;

        try {
            DriveStorageInfo storageInfo = fetchDriveStorageInfo(tokenInfo.getAccessToken(), proxyUrl);
            if (storageInfo != null) {
                tier = inferGoogleOneTier(storageInfo.limit);
                Map<String, Object> extra = new HashMap<>();
                extra.put("drive_storage_limit", storageInfo.limit);
                extra.put("drive_storage_usage", storageInfo.usage);
                extra.put("drive_tier_updated_at", Instant.now().toString());
                tokenInfo.setExtra(extra);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Drive storage info: {}", e.getMessage());
        }

        if (tier == null || tier.isBlank()) {
            tier = TIER_GOOGLE_ONE_FREE;
        }
        tokenInfo.setTierId(canonicalTierId("google_one", tier));
    }

    private void handleAiStudio(GeminiTokenInfo tokenInfo,
                                GeminiOAuthSessionStore.OAuthSession session, String tierId) {
        String tier = tierId;
        if (tier == null || tier.isBlank()) {
            tier = TIER_AI_STUDIO_FREE;
        }
        tokenInfo.setTierId(canonicalTierId("ai_studio", tier));
    }

    private CodeAssistInfo fetchCodeAssistInfo(String accessToken, String proxyUrl) {
        try {
            // This is a simplified implementation
            // The actual Go code calls cloudaicompanion.googleapis.com
            // For now, return null to indicate we couldn't fetch
            log.debug("Code Assist API not implemented in Java backend yet");
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch Code Assist info: {}", e.getMessage());
            return null;
        }
    }

    private DriveStorageInfo fetchDriveStorageInfo(String accessToken, String proxyUrl) {
        try {
            String response = webClient.get()
                    .uri("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .block();

            JsonNode json = objectMapper.readTree(response);
            JsonNode quota = json.get("storageQuota");

            DriveStorageInfo info = new DriveStorageInfo();
            if (quota != null) {
                if (quota.has("limit")) {
                    info.limit = parseStorageValue(quota.get("limit"));
                }
                if (quota.has("usage")) {
                    info.usage = parseStorageValue(quota.get("usage"));
                }
            }
            return info;
        } catch (Exception e) {
            log.warn("Failed to fetch Drive storage info: {}", e.getMessage());
            return null;
        }
    }

    private long parseStorageValue(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        if (node.isNumber()) return node.asLong();
        try {
            return Long.parseLong(node.asText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String inferGoogleOneTier(long storageBytes) {
        if (storageBytes <= 0) return TIER_GOOGLE_ONE_UNKNOWN;
        if (storageBytes > STORAGE_TIER_UNLIMITED) return TIER_GOOGLE_AI_ULTRA;
        if (storageBytes >= STORAGE_TIER_AI_PREMIUM) return TIER_GOOGLE_AI_PRO;
        if (storageBytes >= STORAGE_TIER_FREE) return TIER_GOOGLE_ONE_FREE;
        return TIER_GOOGLE_ONE_UNKNOWN;
    }

    private String canonicalTierId(String oauthType, String tierId) {
        if (tierId == null || tierId.isBlank()) return "";

        tierId = tierId.trim().toLowerCase();

        // Normalize based on OAuth type
        switch (oauthType.toLowerCase()) {
            case "google_one":
                switch (tierId) {
                    case "google_one_free":
                    case "google_ai_pro":
                    case "google_ai_ultra":
                    case "google_one_unknown":
                        return tierId;
                    case "free":
                    case "google_one_basic":
                    case "google_one_standard":
                        return TIER_GOOGLE_ONE_FREE;
                    case "google_one_unlimited":
                        return TIER_GOOGLE_AI_ULTRA;
                    case "ai_premium":
                        return TIER_GOOGLE_AI_PRO;
                    default:
                        return "";
                }
            case "code_assist":
                switch (tierId) {
                    case "gcp_standard":
                    case "gcp_enterprise":
                        return tierId;
                    case "standard":
                    case "pro":
                    case "legacy":
                        return TIER_GCP_STANDARD;
                    case "enterprise":
                    case "ultra":
                        return TIER_GCP_ENTERPRISE;
                    default:
                        return "";
                }
            case "ai_studio":
                switch (tierId) {
                    case "aistudio_free":
                    case "aistudio_paid":
                        return tierId;
                    default:
                        return "";
                }
            default:
                return tierId;
        }
    }

    // ==================== Inner Classes ====================

    private static class OAuthConfig {
        String clientId;
        String clientSecret;
        String scopes;

        OAuthConfig(String clientId, String clientSecret, String scopes) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scopes = scopes;
        }
    }

    private static class TokenResponse {
        String accessToken;
        String refreshToken;
        String tokenType;
        Long expiresIn;
        String scope;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }
        public Long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }

    private static class CodeAssistInfo {
        String projectId;
        String tierId;
    }

    private static class DriveStorageInfo {
        long limit;
        long usage;
    }
}
