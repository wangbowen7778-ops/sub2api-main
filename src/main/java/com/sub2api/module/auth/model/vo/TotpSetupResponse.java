package com.sub2api.module.auth.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TOTP 设置响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "TOTP 设置响应")
public class TotpSetupResponse {

    @Schema(description = "TOTP 密钥 (用于生成二维码)")
    private String secret;

    @Schema(description = "otpauth URI")
    private String otpauthUri;

    @Schema(description = "二维码 Base64 图片")
    private String qrCodeImage;
}
