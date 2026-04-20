package com.sub2api.module.auth.service;

import com.sub2api.module.auth.model.vo.GeminiTokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session store for Gemini OAuth sessions
 * Sessions expire after 30 minutes (configurable)
 */
@Slf4j
@Component
public class GeminiOAuthSessionStore {

    private final Map<String, OAuthSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final int sessionTtlMinutes;

    public GeminiOAuthSessionStore() {
        this(30);
        startCleanupTask();
    }

    public GeminiOAuthSessionStore(int sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
        startCleanupTask();
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * OAuth Session data
     */
    public static class OAuthSession {
        private String state;
        private String codeVerifier;
        private String proxyUrl;
        private String redirectUri;
        private String projectId;
        private String tierId;
        private String oauthType;
        private OffsetDateTime createdAt;
        private GeminiTokenInfo tokenInfo;

        public OAuthSession() {
            this.createdAt = OffsetDateTime.now();
        }

        // Getters and setters
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getCodeVerifier() { return codeVerifier; }
        public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }

        public String getProxyUrl() { return proxyUrl; }
        public void setProxyUrl(String proxyUrl) { this.proxyUrl = proxyUrl; }

        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }

        public String getTierId() { return tierId; }
        public void setTierId(String tierId) { this.tierId = tierId; }

        public String getOauthType() { return oauthType; }
        public void setOauthType(String oauthType) { this.oauthType = oauthType; }

        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

        public GeminiTokenInfo getTokenInfo() { return tokenInfo; }
        public void setTokenInfo(GeminiTokenInfo tokenInfo) { this.tokenInfo = tokenInfo; }

        public boolean isExpired(int ttlMinutes) {
            return createdAt.plusMinutes(ttlMinutes).isBefore(OffsetDateTime.now());
        }
    }

    /**
     * Generate random session ID
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate random state parameter
     */
    public String generateState() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    /**
     * Generate PKCE code verifier (43+ chars per RFC 7636)
     */
    public String generateCodeVerifier() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString() + UUID.randomUUID().toString().substring(0, 27);
    }

    /**
     * Generate PKCE code challenge (S256 method)
     */
    public String generateCodeChallenge(String codeVerifier) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return base64UrlEncode(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    private String base64UrlEncode(byte[] data) {
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        return encoded.replace("+", "-").replace("/", "_").replace("=", "");
    }

    /**
     * Store a session
     */
    public void set(String sessionId, OAuthSession session) {
        sessions.put(sessionId, session);
        log.debug("Stored OAuth session: {}", sessionId);
    }

    /**
     * Get a session by ID
     */
    public OAuthSession get(String sessionId) {
        OAuthSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (session.isExpired(sessionTtlMinutes)) {
            sessions.remove(sessionId);
            log.debug("OAuth session expired: {}", sessionId);
            return null;
        }
        return session;
    }

    /**
     * Delete a session
     */
    public void delete(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Deleted OAuth session: {}", sessionId);
    }

    /**
     * Cleanup expired sessions
     */
    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(sessionTtlMinutes));
        log.debug("Cleaned up expired OAuth sessions");
    }

    /**
     * Shutdown the scheduler
     */
    public void stop() {
        scheduler.shutdown();
    }
}
