package com.sub2api.module.auth.service.platform;

import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.auth.model.vo.OAuthTokenInfo;
import com.sub2api.module.auth.service.OAuthService.OAuthPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Google OAuth 处理器 (用于 Gemini)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthHandler implements OAuthHandler {

    private final AccountService accountService;

    @Override
    public OAuthPlatform getPlatform() {
        return OAuthPlatform.GOOGLE;
    }

    @Override
    public String getAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + getClientId() +
                "&redirect_uri=" + getRedirectUri() +
                "&response_type=code" +
                "&scope=openid email https://www.googleapis.com/auth/generative-language.retriever" +
                "&access_type=offline" +
                "&state=" + state;
    }

    @Override
    public OAuthTokenInfo exchangeCode(String code) {
        // 调用 Google OAuth token endpoint
        throw new UnsupportedOperationException("Google OAuth exchange not implemented");
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        throw new UnsupportedOperationException("Google OAuth user info not implemented");
    }

    @Override
    public Account createOrUpdateAccount(Long userId, OAuthTokenInfo tokenInfo, Map<String, Object> userInfo) {
        Account account = new Account();
        account.setPlatform("google");
        account.setType("oauth");
        account.setName((String) userInfo.get("name"));
        account.setStatus("active");
        account.setSchedulable(true);
        account.setCredentialExpiredAt(LocalDateTime.now().plusSeconds(tokenInfo.getExpiresIn()));
        account.setCredentials(Map.of(
                "access_token", tokenInfo.getAccessToken(),
                "refresh_token", tokenInfo.getRefreshToken() != null ? tokenInfo.getRefreshToken() : ""
        ));
        return account;
    }

    private String getClientId() {
        return System.getProperty("oauth.google.client-id", "");
    }

    private String getRedirectUri() {
        return System.getProperty("oauth.google.redirect-uri", "");
    }
}
