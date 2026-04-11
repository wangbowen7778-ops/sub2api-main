package com.sub2api.module.gateway.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号信息响应
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账号信息")
public class ProxyRequestVo {

    @Schema(description = "账号ID")
    private Long id;

    @Schema(description = "账号名称")
    private String name;

    @Schema(description = "平台")
    private String platform;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "负载因子")
    private BigDecimal loadFactor;

    @Schema(description = "最大并发")
    private Integer maxConcurrency;

    @Schema(description = "当前并发")
    private Integer currentConcurrency;

    @Schema(description = "最后使用时间")
    private LocalDateTime lastUsedAt;

    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;
}
