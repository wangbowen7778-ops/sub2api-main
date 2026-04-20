package com.sub2api.module.auth.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth Token 信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTokenInfo {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 过期时间（秒）
     */
    private long expiresIn;

    /**
     * 过期时间点
     */
    private java.time.OffsetDateTime expiresAt;

    /**
     * Token 类型
     */
    private String tokenType;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 用户 ID
     */
    private String userId;
}
