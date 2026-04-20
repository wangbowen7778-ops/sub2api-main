package com.sub2api.module.apikey.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 API Key 请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "创建 API Key 请求")
public class CreateApiKeyRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称最长100字符")
    @Schema(description = "API Key 名称")
    private String name;

    @Schema(description = "关联的分组ID")
    private Long groupId;

    @Schema(description = "自定义Key (可选，最少16字符)")
    private String customKey;

    @Schema(description = "IP 白名单")
    private List<String> ipWhitelist;

    @Schema(description = "IP 黑名单")
    private List<String> ipBlacklist;

    @Schema(description = "配额限制 (USD, 0=无限制)")
    private BigDecimal quota;

    @Schema(description = "过期天数 (null=永不过期)")
    private Integer expiresInDays;

    @Schema(description = "5小时费率限制 (USD, 0=无限制)")
    private BigDecimal rateLimit5h;

    @Schema(description = "日费率限制 (USD, 0=无限制)")
    private BigDecimal rateLimit1d;

    @Schema(description = "周费率限制 (USD, 0=无限制)")
    private BigDecimal rateLimit7d;
}