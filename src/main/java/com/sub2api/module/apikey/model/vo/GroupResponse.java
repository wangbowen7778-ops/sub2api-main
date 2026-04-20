package com.sub2api.module.apikey.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分组响应 - 用于API Key响应中的关联分组信息
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Schema(description = "分组响应")
public class GroupResponse {

    @Schema(description = "分组ID")
    private Long id;

    @Schema(description = "分组名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "平台")
    private String platform;

    @JsonProperty("rate_multiplier")
    @Schema(description = "费率倍率")
    private Double rateMultiplier;

    @JsonProperty("is_exclusive")
    @Schema(description = "是否专属分组")
    private Boolean isExclusive;

    @Schema(description = "状态")
    private String status;

    @JsonProperty("subscription_type")
    @Schema(description = "订阅类型")
    private String subscriptionType;

    @JsonProperty("daily_limit_usd")
    @Schema(description = "日限额 (USD)")
    private Double dailyLimitUsd;

    @JsonProperty("weekly_limit_usd")
    @Schema(description = "周限额 (USD)")
    private Double weeklyLimitUsd;

    @JsonProperty("monthly_limit_usd")
    @Schema(description = "月限额 (USD)")
    private Double monthlyLimitUsd;
}