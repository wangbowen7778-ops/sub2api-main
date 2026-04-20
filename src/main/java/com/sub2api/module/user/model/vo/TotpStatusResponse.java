package com.sub2api.module.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TOTP 状态响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "TOTP 状态响应")
public class TotpStatusResponse {

    @Schema(description = "是否已启用 TOTP")
    private Boolean enabled;

    @Schema(description = "启用时间戳 (Unix)")
    private Long enabledAt;

    @Schema(description = "TOTP 功能是否全局启用")
    private Boolean featureEnabled;
}
