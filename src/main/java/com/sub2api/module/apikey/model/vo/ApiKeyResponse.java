package com.sub2api.module.apikey.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sub2api.module.user.model.vo.UserVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API Key 响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "API Key 响应")
public class ApiKeyResponse {

    @Schema(description = "API Key ID")
    private Long id;

    @Schema(description = "用户ID")
    @JsonProperty("user_id")
    private Long userId;

    @Schema(description = "Key 值")
    private String key;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "分组ID")
    @JsonProperty("group_id")
    private Long groupId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "IP 白名单")
    @JsonProperty("ip_whitelist")
    private List<String> ipWhitelist;

    @Schema(description = "IP 黑名单")
    @JsonProperty("ip_blacklist")
    private List<String> ipBlacklist;

    @Schema(description = "最后使用时间")
    @JsonProperty("last_used_at")
    private OffsetDateTime lastUsedAt;

    @Schema(description = "配额限制 (USD)")
    private BigDecimal quota;

    @Schema(description = "已使用配额 (USD)")
    @JsonProperty("quota_used")
    private BigDecimal quotaUsed;

    @Schema(description = "过期时间")
    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;

    @Schema(description = "创建时间")
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    // ========== Rate limit fields ==========

    @Schema(description = "5小时费率限制 (USD)")
    @JsonProperty("rate_limit_5h")
    private BigDecimal rateLimit5h;

    @Schema(description = "日费率限制 (USD)")
    @JsonProperty("rate_limit_1d")
    private BigDecimal rateLimit1d;

    @Schema(description = "周费率限制 (USD)")
    @JsonProperty("rate_limit_7d")
    private BigDecimal rateLimit7d;

    @Schema(description = "当前 5h 使用量")
    private BigDecimal usage5h;

    @Schema(description = "当前 1d 使用量")
    private BigDecimal usage1d;

    @Schema(description = "当前 7d 使用量")
    private BigDecimal usage7d;

    @Schema(description = "5h 窗口开始时间")
    @JsonProperty("window_5h_start")
    private OffsetDateTime window5hStart;

    @Schema(description = "1d 窗口开始时间")
    @JsonProperty("window_1d_start")
    private OffsetDateTime window1dStart;

    @Schema(description = "7d 窗口开始时间")
    @JsonProperty("window_7d_start")
    private OffsetDateTime window7dStart;

    // ========== Computed fields ==========

    @JsonProperty("reset_5h_at")
    private OffsetDateTime reset5hAt;

    @JsonProperty("reset_1d_at")
    private OffsetDateTime reset1dAt;

    @JsonProperty("reset_7d_at")
    private OffsetDateTime reset7dAt;

    // ========== Relations (optional) ==========

    private UserVO user;

    private GroupResponse group;
}