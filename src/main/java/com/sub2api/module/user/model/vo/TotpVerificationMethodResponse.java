package com.sub2api.module.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TOTP 验证方式响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "TOTP 验证方式响应")
public class TotpVerificationMethodResponse {

    @Schema(description = "验证方式: email 或 password")
    private String method;
}
