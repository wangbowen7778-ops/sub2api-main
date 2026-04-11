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
 * Anthropic OAuth 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicOAuthHandler implements OAuthHandler {

    private final AccountService accountService;

    @Override
    public OAuthPlatform getPlatform() {
        return OAuthPlatform.ANTHROPIC;
    }

    @Override
    public String getAuthorizationUrl(String state) {
        // Anthropic 使用 OAuth 2.0
        return "https://auth.anthropic.com/oauth/authorize?" +
                "client_id=" + getClientId() +
                "&redirect_uri=" + getRedirectUri() +
                "&response_type=code" +
                "&scope=api:read api:write" +
                "&state=" + state;
    }

    @Override
    public OAuthTokenInfo exchangeCode(String code) {
        // 调用 Anthropic OAuth token endpoint
        // 这里简化处理，实际需要调用外部 API
        throw new UnsupportedOperationException("Anthropic OAuth exchange not implemented");
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        // 获取用户信息
        throw new UnsupportedOperationException("Anthropic OAuth user info not implemented");
    }

    @Override
    public Account createOrUpdateAccount(Long userId, OAuthTokenInfo tokenInfo, Map<String, Object> userInfo) {
        // 创建或更新账号
        Account account = new Account();
        account.setPlatform("anthropic");
        account.setType("oauth");
        account.setName((String) userInfo.get("name"));
        account.setStatus("active");
        account.setSchedulable(true);
        account.setCredentialExpiredAt(LocalDateTime.now().plusSeconds(tokenInfo.getExpiresIn()));
        // 设置凭证
        account.setCredentials(Map.of(
                "access_token", tokenInfo.getAccessToken(),
                "refresh_token", tokenInfo.getRefreshToken() != null ? tokenInfo.getRefreshToken() : ""
        ));
        return account;
    }

    private String getClientId() {
        // 从配置获取
        return System.getProperty("oauth.anthropic.client-id", "");
    }

    private String getRedirectUri() {
        return System.getProperty("oauth.anthropic.redirect-uri", "");
    }
}
