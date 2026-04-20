package com.sub2api.module.user.model.vo;

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

    @Schema(description = "TOTP 密钥")
    private String secret;

    @Schema(description = "二维码 URL")
    private String qrCodeUrl;

    @Schema(description = "设置令牌")
    private String setupToken;

    @Schema(description = "过期倒计时 (秒)")
    private Integer countdown;
}
