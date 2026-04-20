package com.sub2api.module.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TOTP 启用请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "TOTP 启用请求")
public class TotpEnableRequest {

    @NotBlank(message = "TOTP 验证码不能为空")
    @Schema(description = "TOTP 验证码 (6位)")
    private String totpCode;

    @NotBlank(message = "设置令牌不能为空")
    @Schema(description = "设置令牌")
    private String setupToken;
}
