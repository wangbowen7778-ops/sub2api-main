package com.sub2api.module.apikey.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 更新 API Key 请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "更新 API Key 请求")
public class UpdateApiKeyRequest {

    @Size(max = 100, message = "名称最长100字符")
    @Schema(description = "API Key 名称")
    private String name;

    @Schema(description = "关联的分组ID (null=不修改, 0=解绑, >0=绑定到目标分组)")
    private Long groupId;

    @Schema(description = "状态 (active/inactive)")
    private String status;

    @Schema(description = "IP 白名单 (空数组清空)")
    private List<String> ipWhitelist;

    @Schema(description = "IP 黑名单 (空数组清空)")
    private List<String> ipBlacklist;

    @Schema(description = "配额限制 (USD, null=不修改, 0=无限制)")
    private BigDecimal quota;

    @Schema(description = "过期时间 (null=不修改, 空字符串=清除过期)")
    private OffsetDateTime expiresAt;

    @Schema(description = "是否清除过期时间")
    private Boolean clearExpiration;

    @Schema(description = "重置已用配额")
    private Boolean resetQuota;

    @Schema(description = "5小时费率限制 (USD, null=不修改, 0=无限制)")
    private BigDecimal rateLimit5h;

    @Schema(description = "日费率限制 (USD, null=不修改, 0=无限制)")
    private BigDecimal rateLimit1d;

    @Schema(description = "周费率限制 (USD, null=不修改, 0=无限制)")
    private BigDecimal rateLimit7d;

    @Schema(description = "重置费率限制使用量")
    private Boolean resetRateLimitUsage;
}