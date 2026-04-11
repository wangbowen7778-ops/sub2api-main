package com.sub2api.module.auth.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TOTP 验证请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "TOTP 验证请求")
public class TotpVerifyRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "TOTP 验证码不能为空")
    @Schema(description = "TOTP 验证码")
    private String code;
}
