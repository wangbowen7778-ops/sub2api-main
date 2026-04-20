package com.sub2api.module.auth.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Gemini OAuth Capabilities Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiOAuthCapabilities {

    /**
     * Whether AI Studio OAuth is enabled
     */
    private boolean aiStudioOAuthEnabled;

    /**
     * Required redirect URIs
     */
    private String[] requiredRedirectUris;
}
