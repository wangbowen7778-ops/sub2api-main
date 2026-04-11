package com.sub2api.module.billing.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用量日志实体
 * 表名: usage_logs
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("usage_logs")
public class UsageLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用量ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 模型
     */
    private String model;

    /**
     * 客户端请求的模型名称
     */
    private String requestedModel;

    /**
     * 上游实际使用的模型名称
     */
    private String upstreamModel;

    /**
     * 渠道ID
     */
    private Long channelId;

    /**
     * 模型映射链
     */
    private String modelMappingChain;

    /**
     * 计费层级标签
     */
    private String billingTier;

    /**
     * 计费模式: token, per_request, image
     */
    private String billingMode;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 订阅ID
     */
    private Long subscriptionId;

    /**
     * 输入 Token 数
     */
    private Integer inputTokens;

    /**
     * 输出 Token 数
     */
    private Integer outputTokens;

    /**
     * 缓存创建 Token 数
     */
    private Integer cacheCreationTokens;

    /**
     * 缓存读取 Token 数
     */
    private Integer cacheReadTokens;

    /**
     * 5分钟缓存创建 Token 数
     */
    private Integer cacheCreation5mTokens;

    /**
     * 1小时缓存创建 Token 数
     */
    private Integer cacheCreation1hTokens;

    /**
     * 输入成本
     */
    private BigDecimal inputCost;

    /**
     * 输出成本
     */
    private BigDecimal outputCost;

    /**
     * 缓存创建成本
     */
    private BigDecimal cacheCreationCost;

    /**
     * 缓存读取成本
     */
    private BigDecimal cacheReadCost;

    /**
     * 总成本
     */
    private BigDecimal totalCost;

    /**
     * 实际成本
     */
    private BigDecimal actualCost;

    /**
     * 费率倍率
     */
    private BigDecimal rateMultiplier;

    /**
     * 账号费率倍率快照
     */
    private BigDecimal accountRateMultiplier;

    /**
     * 计费类型
     */
    private Byte billingType;

    /**
     * 是否为流式响应
     */
    private Boolean stream;

    /**
     * 请求耗时 (毫秒)
     */
    private Integer durationMs;

    /**
     * 首个 Token 耗时 (毫秒)
     */
    private Integer firstTokenMs;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * IP 地址
     */
    private String ipAddress;

    /**
     * 图片数量
     */
    private Integer imageCount;

    /**
     * 图片尺寸
     */
    private String imageSize;

    /**
     * 缓存 TTL 是否被覆盖
     */
    private Boolean cacheTtlOverridden;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
