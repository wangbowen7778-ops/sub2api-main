package com.sub2api.module.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini OAuth configuration properties
 * Maps to sub2api.gemini.oauth.* in application.yml
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sub2api.gemini.oauth")
public class GeminiOAuthConfig {

    /**
     * OAuth Client ID for custom Gemini OAuth
     */
    private String clientId;

    /**
     * OAuth Client Secret for custom Gemini OAuth
     */
    private String clientSecret;

    /**
     * OAuth Scopes (space-separated)
     */
    private String scopes;

    /**
     * Built-in Gemini CLI OAuth Client ID
     */
    public static final String BUILT_IN_CLIENT_ID = "681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com";

    /**
     * Built-in Gemini CLI OAuth Client Secret Env Var Name
     */
    public static final String BUILT_IN_CLIENT_SECRET_ENV = "GEMINI_CLI_OAUTH_CLIENT_SECRET";

    /**
     * AI Studio OAuth Redirect URI
     */
    public static final String AI_STUDIO_REDIRECT_URI = "http://localhost:1455/auth/callback";

    /**
     * Gemini CLI Redirect URI
     */
    public static final String GEMINI_CLI_REDIRECT_URI = "https://codeassist.google.com/authcode";

    /**
     * Default Code Assist Scopes
     */
    public static final String DEFAULT_CODE_ASSIST_SCOPES =
            "https://www.googleapis.com/auth/cloud-platform " +
            "https://www.googleapis.com/auth/userinfo.email " +
            "https://www.googleapis.com/auth/userinfo.profile";

    /**
     * Default AI Studio Scopes
     */
    public static final String DEFAULT_AI_STUDIO_SCOPES =
            "https://www.googleapis.com/auth/cloud-platform " +
            "https://www.googleapis.com/auth/generative-language.retriever";

    /**
     * Google OAuth Authorization URL
     */
    public static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    /**
     * Google OAuth Token URL
     */
    public static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    /**
     * Session TTL in minutes
     */
    private int sessionTtlMinutes = 30;
}
