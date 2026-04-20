package com.sub2api.module.auth.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sub2api.module.user.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 是否需要双因素认证
     */
    @JsonProperty("require_mfa")
    private Boolean requireMfa;

    /**
     * 访问令牌
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * 刷新令牌
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 令牌类型
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * 过期时间 (秒)
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * 用户信息
     */
    @JsonProperty("user")
    private UserVO user;
}
