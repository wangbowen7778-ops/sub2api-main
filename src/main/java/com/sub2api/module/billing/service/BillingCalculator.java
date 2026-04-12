package com.sub2api.module.billing.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计费计算服务
 * 支持按 token 计费、缓存计费、服务等级、长期上下文等
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
public class BillingCalculator {

    /**
     * 默认费率 (每 1M tokens 的价格，单位：分)
     */
    private static final BigDecimal DEFAULT_INPUT_RATE = new BigDecimal("0.5");
    private static final BigDecimal DEFAULT_OUTPUT_RATE = new BigDecimal("1.5");

    /**
     * 模型定价配置
     */
    @Data
    public static class ModelPricing {
        private BigDecimal inputPricePerToken = BigDecimal.ZERO;
        private BigDecimal inputPricePerTokenPriority = BigDecimal.ZERO;
        private BigDecimal outputPricePerToken = BigDecimal.ZERO;
        private BigDecimal outputPricePerTokenPriority = BigDecimal.ZERO;
        private BigDecimal cacheCreationPricePerToken = BigDecimal.ZERO;
        private BigDecimal cacheReadPricePerToken = BigDecimal.ZERO;
        private BigDecimal cacheReadPricePerTokenPriority = BigDecimal.ZERO;
        private BigDecimal cacheCreation5mPrice = BigDecimal.ZERO;
        private BigDecimal cacheCreation1hPrice = BigDecimal.ZERO;
        private boolean supportsCacheBreakdown = false;
        private int longContextInputThreshold = 0;
        private BigDecimal longContextInputMultiplier = BigDecimal.ONE;
        private BigDecimal longContextOutputMultiplier = BigDecimal.ONE;
        private BigDecimal imageOutputPricePerToken = BigDecimal.ZERO;
    }

    /**
     * 使用的 token 数量
     */
    @Data
    public static class UsageTokens {
        private int inputTokens = 0;
        private int outputTokens = 0;
        private int cacheCreationTokens = 0;
        private int cacheReadTokens = 0;
        private int cacheCreation5mTokens = 0;
        private int cacheCreation1hTokens = 0;
        private int imageOutputTokens = 0;
    }

    /**
     * 计费上下文
     */
    @Data
    public static class BillingContext {
        private String serviceTier = "standard";
        private ModelPricing pricing;
        private int inputTokens;
        private boolean isLongContext = false;
    }

    /**
     * 计算费用 (单位: 分)
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @param rateMultiplier 计费倍率
     * @return 费用 (分)
     */
    public Long calculateCost(Long inputTokens, Long outputTokens, BigDecimal rateMultiplier) {
        if (inputTokens == null) inputTokens = 0L;
        if (outputTokens == null) outputTokens = 0L;
        if (rateMultiplier == null) rateMultiplier = BigDecimal.ONE;

        BigDecimal inputCost = calculateTokenCost(inputTokens, DEFAULT_INPUT_RATE);
        BigDecimal outputCost = calculateTokenCost(outputTokens, DEFAULT_OUTPUT_RATE);

        BigDecimal totalCost = inputCost.add(outputCost)
                .multiply(rateMultiplier)
                .setScale(0, RoundingMode.HALF_UP);

        return totalCost.longValue();
    }

    /**
     * 计算费用（使用模型定价）
     *
     * @param tokens     使用的 token 数量
     * @param pricing    模型定价
     * @param context    计费上下文
     * @return 费用 (分)
     */
    public Long calculateCost(UsageTokens tokens, ModelPricing pricing, BillingContext context) {
        if (tokens == null || pricing == null) {
            return 0L;
        }

        boolean usePriority = usePriorityServiceTierPricing(context);
        BigDecimal cost = BigDecimal.ZERO;

        // 输入 token 费用
        if (tokens.getInputTokens() > 0) {
            BigDecimal inputRate = usePriority && pricing.getInputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0
                    ? pricing.getInputPricePerTokenPriority()
                    : pricing.getInputPricePerToken();

            if (inputRate.compareTo(BigDecimal.ZERO) == 0) {
                inputRate = DEFAULT_INPUT_RATE;
            }

            // 长期上下文输入费用计算
            if (pricing.getLongContextInputThreshold() > 0 &&
                    tokens.getInputTokens() > pricing.getLongContextInputThreshold() &&
                    pricing.getLongContextInputMultiplier().compareTo(BigDecimal.ONE) > 0) {
                // 长期上下文：输入费用 * 倍率
                cost = cost.add(calculateTokenCost((long) tokens.getInputTokens(), inputRate)
                        .multiply(pricing.getLongContextInputMultiplier()));
            } else {
                cost = cost.add(calculateTokenCost((long) tokens.getInputTokens(), inputRate));
            }
        }

        // 输出 token 费用
        if (tokens.getOutputTokens() > 0) {
            BigDecimal outputRate = usePriority && pricing.getOutputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0
                    ? pricing.getOutputPricePerTokenPriority()
                    : pricing.getOutputPricePerToken();

            if (outputRate.compareTo(BigDecimal.ZERO) == 0) {
                outputRate = DEFAULT_OUTPUT_RATE;
            }

            // 长期上下文输出费用计算
            if (pricing.getLongContextOutputMultiplier().compareTo(BigDecimal.ONE) > 0) {
                cost = cost.add(calculateTokenCost((long) tokens.getOutputTokens(), outputRate)
                        .multiply(pricing.getLongContextOutputMultiplier()));
            } else {
                cost = cost.add(calculateTokenCost((long) tokens.getOutputTokens(), outputRate));
            }
        }

        // 缓存创建费用
        if (pricing.isSupportsCacheBreakdown() && tokens.getCacheCreationTokens() > 0) {
            cost = cost.add(calculateTokenCost((long) tokens.getCacheCreationTokens(), pricing.getCacheCreationPricePerToken()));
        }

        // 缓存读取费用
        if (pricing.isSupportsCacheBreakdown() && tokens.getCacheReadTokens() > 0) {
            BigDecimal cacheReadRate = usePriority && pricing.getCacheReadPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0
                    ? pricing.getCacheReadPricePerTokenPriority()
                    : pricing.getCacheReadPricePerToken();
            cost = cost.add(calculateTokenCost((long) tokens.getCacheReadTokens(), cacheReadRate));
        }

        // 5分钟缓存创建费用
        if (tokens.getCacheCreation5mTokens() > 0) {
            cost = cost.add(calculateTokenCost((long) tokens.getCacheCreation5mTokens(), pricing.getCacheCreation5mPrice()));
        }

        // 1小时缓存创建费用
        if (tokens.getCacheCreation1hTokens() > 0) {
            cost = cost.add(calculateTokenCost((long) tokens.getCacheCreation1hTokens(), pricing.getCacheCreation1hPrice()));
        }

        // 图片输出费用
        if (tokens.getImageOutputTokens() > 0 && pricing.getImageOutputPricePerToken().compareTo(BigDecimal.ZERO) > 0) {
            cost = cost.add(calculateTokenCost((long) tokens.getImageOutputTokens(), pricing.getImageOutputPricePerToken()));
        }

        // 应用服务等级倍率
        BigDecimal tierMultiplier = serviceTierCostMultiplier(context);
        cost = cost.multiply(tierMultiplier).setScale(0, RoundingMode.HALF_UP);

        return cost.longValue();
    }

    /**
     * 判断是否使用优先服务等级定价
     */
    private boolean usePriorityServiceTierPricing(BillingContext context) {
        if (context == null || context.getServiceTier() == null) {
            return false;
        }
        return "priority".equalsIgnoreCase(context.getServiceTier().trim());
    }

    /**
     * 获取服务等级费用倍率
     */
    private BigDecimal serviceTierCostMultiplier(BillingContext context) {
        if (context == null || context.getServiceTier() == null) {
            return BigDecimal.ONE;
        }
        switch (context.getServiceTier().toLowerCase().trim()) {
            case "priority":
                return new BigDecimal("2.0");
            case "flex":
                return new BigDecimal("0.5");
            default:
                return BigDecimal.ONE;
        }
    }

    /**
     * 计算 Token 费用
     *
     * @param tokens token 数量
     * @param ratePerMillion 每百万费率
     * @return 费用
     */
    private BigDecimal calculateTokenCost(long tokens, BigDecimal ratePerMillion) {
        if (tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP)
                .multiply(ratePerMillion);
    }

    /**
     * 计算 Token 费用
     *
     * @param tokens token 数量
     * @param ratePerMillion 每百万费率
     * @return 费用
     */
    private BigDecimal calculateTokenCost(Long tokens, BigDecimal ratePerMillion) {
        if (tokens == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return calculateTokenCost(tokens.longValue(), ratePerMillion);
    }

    /**
     * 计算图片生成费用
     *
     * @param imageCount 图片数量
     * @param pricePerThousand 每千张价格
     * @return 费用
     */
    public Long calculateImageCost(Integer imageCount, BigDecimal pricePerThousand) {
        if (imageCount == null || imageCount <= 0) {
            return 0L;
        }
        if (pricePerThousand == null) {
            pricePerThousand = BigDecimal.ONE;
        }
        return BigDecimal.valueOf(imageCount)
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                .multiply(pricePerThousand)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 计算简单费用（用于兼容旧接口）
     */
    public Long calculateSimpleCost(Long inputTokens, Long outputTokens, BigDecimal rateMultiplier) {
        return calculateCost(inputTokens, outputTokens, rateMultiplier);
    }
}
