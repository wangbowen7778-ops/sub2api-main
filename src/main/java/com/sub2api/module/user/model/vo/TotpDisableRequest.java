package com.sub2api.module.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * TOTP 禁用请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "TOTP 禁用请求")
public class TotpDisableRequest {

    @Schema(description = "邮箱验证码 (当使用邮箱验证时)")
    private String emailCode;

    @Schema(description = "密码 (当使用密码验证时)")
    private String password;
}
