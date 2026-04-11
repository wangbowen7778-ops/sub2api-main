package com.sub2api.module.auth.service.platform;

import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.auth.service.OAuthService.OAuthPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OpenAI OAuth 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIOAuthHandler implements OAuthHandler {

    private final AccountService accountService;

    @Override
    public OAuthPlatform getPlatform() {
        return OAuthPlatform.OPENAI;
    }

    @Override
    public String getAuthorizationUrl(String state) {
        return "https://oauth.openai.com/authorize?" +
                "client_id=" + getClientId() +
                "&redirect_uri=" + getRedirectUri() +
                "&response_type=code" +
                "&scope=openid email model.read" +
                "&state=" + state;
    }

    @Override
    public OAuthTokenInfo exchangeCode(String code) {
        // 调用 OpenAI OAuth token endpoint
        throw new UnsupportedOperationException("OpenAI OAuth exchange not implemented");
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        throw new UnsupportedOperationException("OpenAI OAuth user info not implemented");
    }

    @Override
    public Account createOrUpdateAccount(Long userId, OAuthTokenInfo tokenInfo, Map<String, Object> userInfo) {
        Account account = new Account();
        account.setPlatform("openai");
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
        return System.getProperty("oauth.openai.client-id", "");
    }

    private String getRedirectUri() {
        return System.getProperty("oauth.openai.redirect-uri", "");
    }
}
