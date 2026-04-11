package com.sub2api.module.billing.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 计费统计响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "计费统计")
public class BillingStatistics {

    @Schema(description = "总输入 Token 数")
    private Long totalInputTokens;

    @Schema(description = "总输出 Token 数")
    private Long totalOutputTokens;

    @Schema(description = "总费用 (USD)")
    private BigDecimal totalCost;

    @Schema(description = "总请求数")
    private Long totalRequests;

    @Schema(description = "平均每请求费用")
    private BigDecimal avgCostPerRequest;
}
