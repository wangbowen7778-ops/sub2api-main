package com.sub2api.module.auth.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gemini OAuth Authorization URL Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiAuthUrlVO {

    /**
     * Authorization URL to redirect user to
     */
    private String authUrl;

    /**
     * Session ID for code exchange
     */
    private String sessionId;

    /**
     * State parameter for CSRF protection
     */
    private String state;
}
