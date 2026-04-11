package com.sub2api.module.auth.service.platform;

import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.auth.service.OAuthService.OAuthPlatform;

import java.util.Map;

/**
 * OAuth 处理器接口
 */
public interface OAuthHandler {

    /**
     * 获取平台
     */
    OAuthPlatform getPlatform();

    /**
     * 获取授权 URL
     */
    String getAuthorizationUrl(String state);

    /**
     * 交换授权码获取 Token
     */
    OAuthTokenInfo exchangeCode(String code);

    /**
     * 获取用户信息
     */
    Map<String, Object> getUserInfo(String accessToken);

    /**
     * 创建或更新账号
     */
    Account createOrUpdateAccount(Long userId, OAuthTokenInfo tokenInfo, Map<String, Object> userInfo);
}
