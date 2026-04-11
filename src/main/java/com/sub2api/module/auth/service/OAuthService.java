package com.sub2api.module.auth.service;

import com.sub2api.module.auth.model.vo.LoginResponse;
import com.sub2api.module.auth.model.vo.OAuthTokenInfo;
import com.sub2api.module.auth.service.platform.OAuthHandler;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth 服务
 * <p>
 * 支持: Anthropic, OpenAI, Google, LinuxDo, OIDC
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
public class OAuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final Map<OAuthPlatform, OAuthHandler> handlers = new ConcurrentHashMap<>();

    public OAuthService(UserService userService, JwtService jwtService,
                       List<OAuthHandler> oauthHandlers) {
        this.userService = userService;
        this.jwtService = jwtService;

        // 注册所有 OAuth 处理器
        for (OAuthHandler handler : oauthHandlers) {
            handlers.put(handler.getPlatform(), handler);
        }
    }

    /**
     * OAuth 平台枚举
     */
    public enum OAuthPlatform {
        ANTHROPIC("anthropic", "Anthropic"),
        OPENAI("openai", "OpenAI"),
        GOOGLE("google", "Google"),
        LINUXDO("linuxdo", "LinuxDo"),
        OIDC("oidc", "OpenID Connect");

        private final String id;
        private final String displayName;

        OAuthPlatform(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static OAuthPlatform fromId(String id) {
            for (OAuthPlatform platform : values()) {
                if (platform.id.equals(id)) {
                    return platform;
                }
            }
            return null;
        }
    }

    /**
     * 获取 OAuth 处理器
     */
    public OAuthHandler getHandler(OAuthPlatform platform) {
        OAuthHandler handler = handlers.get(platform);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的 OAuth 平台: " + platform);
        }
        return handler;
    }

    /**
     * 获取授权 URL
     */
    public String getAuthorizationUrl(OAuthPlatform platform, String state) {
        OAuthHandler handler = getHandler(platform);
        return handler.getAuthorizationUrl(state);
    }

    /**
     * 处理 OAuth 回调
     */
    public LoginResponse handleOAuthCallback(OAuthPlatform platform, String code) {
        OAuthHandler handler = getHandler(platform);

        // 交换 token
        OAuthTokenInfo tokenInfo = handler.exchangeCode(code);

        // 获取用户信息
        Map<String, Object> userInfo = handler.getUserInfo(tokenInfo.getAccessToken());

        String externalId = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");

        if (externalId == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAIL, "OAuth 返回的用户信息不完整");
        }

        // 查找或创建用户
        String username = generateUsername(platform, externalId);
        User user = findOrCreateOAuthUser(platform, externalId, email, name);

        // 检查用户状态
        userService.checkUserStatus(user);

        // 创建或更新账号
        handler.createOrUpdateAccount(user.getId(), tokenInfo, userInfo);

        // 生成令牌
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("OAuth 登录成功: platform={}, username={}", platform.getDisplayName(), user.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    /**
     * 生成 OAuth 用户名
     */
    private String generateUsername(OAuthPlatform platform, String externalId) {
        return platform.getId() + "_" + externalId.substring(0, Math.min(8, externalId.length()));
    }

    /**
     * 查找或创建 OAuth 用户
     */
    private User findOrCreateOAuthUser(OAuthPlatform platform, String externalId, String email, String name) {
        String username = generateUsername(platform, externalId);

        User user = userService.findByUsername(username);
        if (user != null) {
            return user;
        }

        // 创建新用户
        String randomPassword = UUID.randomUUID().toString();
        String passwordHash = com.sub2api.module.common.util.EncryptionUtil.hashPassword(randomPassword, "");

        User newUser = userService.createUser(email, passwordHash, "user", null);
        newUser.setUsername(username);
        userService.updateUser(newUser);
        return newUser;
    }

    /**
     * 获取 OAuth 授权 URL (简化版)
     */
    public String getAuthorizationUrl(OAuthPlatform platform) {
        return "/oauth2/authorization/" + platform.getId();
    }
}
