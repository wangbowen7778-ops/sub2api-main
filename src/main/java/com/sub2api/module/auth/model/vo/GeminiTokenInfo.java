package com.sub2api.module.auth.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Gemini OAuth Token Info Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiTokenInfo {

    /**
     * Access token
     */
    private String accessToken;

    /**
     * Refresh token
     */
    private String refreshToken;

    /**
     * Token type (usually "Bearer")
     */
    private String tokenType;

    /**
     * Expires in seconds
     */
    private Long expiresIn;

    /**
     * Expiration timestamp (Unix epoch seconds)
     */
    private Long expiresAt;

    /**
     * OAuth scope
     */
    private String scope;

    /**
     * Project ID (for Code Assist)
     */
    private String projectId;

    /**
     * OAuth type: "code_assist", "google_one", or "ai_studio"
     */
    private String oauthType;

    /**
     * Tier ID (e.g., "google_one_free", "gcp_standard")
     */
    private String tierId;

    /**
     * Extra metadata (e.g., drive storage info)
     */
    private Map<String, Object> extra;
}
