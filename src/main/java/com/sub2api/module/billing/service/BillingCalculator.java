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
     * 计费模式
     */
    public enum BillingMode {
        TOKEN("token"),
        PER_REQUEST("per_request"),
        IMAGE("image");

        private final String value;

        BillingMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static BillingMode fromValue(String value) {
            if (value == null || value.isBlank()) {
                return TOKEN;
            }
            for (BillingMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return TOKEN;
        }
    }

    /**
     * 费用明细
     */
    @Data
    public static class CostBreakdown {
        private double inputCost = 0;
        private double outputCost = 0;
        private double imageOutputCost = 0;
        private double cacheCreationCost = 0;
        private double cacheReadCost = 0;
        private double totalCost = 0;
        private double actualCost = 0; // 应用倍率后的实际费用
        private BillingMode billingMode = BillingMode.TOKEN; // 计费模式
    }

    /**
     * 图片计费配置
     */
    @Data
    public static class ImagePriceConfig {
        private BigDecimal price1K; // 1K 尺寸价格
        private BigDecimal price2K; // 2K 尺寸价格
        private BigDecimal price4K; // 4K 尺寸价格
    }

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

    /**
     * 计算 Token 费用明细
     *
     * @param pricing         模型定价
     * @param tokens          使用的 token 数量
     * @param rateMultiplier  计费倍率
     * @param serviceTier     服务等级 ("priority", "flex", "")
     * @param applyLongCtx    是否应用长上下文定价
     * @return 费用明细
     */
    public CostBreakdown computeTokenBreakdown(ModelPricing pricing, UsageTokens tokens,
                                                double rateMultiplier, String serviceTier,
                                                boolean applyLongCtx) {
        if (rateMultiplier <= 0) {
            rateMultiplier = 1.0;
        }

        BigDecimal inputPrice = pricing.getInputPricePerToken();
        BigDecimal outputPrice = pricing.getOutputPricePerToken();
        BigDecimal cacheReadPrice = pricing.getCacheReadPricePerToken();
        double tierMultiplier = 1.0;

        boolean usePriority = usePriorityServiceTierPricing(serviceTier, pricing);
        if (usePriority) {
            if (pricing.getInputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0) {
                inputPrice = pricing.getInputPricePerTokenPriority();
            }
            if (pricing.getOutputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0) {
                outputPrice = pricing.getOutputPricePerTokenPriority();
            }
            if (pricing.getCacheReadPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0) {
                cacheReadPrice = pricing.getCacheReadPricePerTokenPriority();
            }
        } else {
            tierMultiplier = serviceTierCostMultiplier(serviceTier);
        }

        if (applyLongCtx && shouldApplySessionLongContextPricing(tokens, pricing)) {
            inputPrice = inputPrice.multiply(pricing.getLongContextInputMultiplier());
            outputPrice = outputPrice.multiply(pricing.getLongContextOutputMultiplier());
        }

        CostBreakdown bd = new CostBreakdown();
        bd.setBillingMode(BillingMode.TOKEN);

        // 输入 token 费用
        bd.setInputCost(calculateTokenCost(tokens.getInputTokens(), inputPrice).doubleValue());

        // 分离图片输出 token 与文本输出 token
        int textOutputTokens = tokens.getOutputTokens() - tokens.getImageOutputTokens();
        if (textOutputTokens < 0) {
            textOutputTokens = 0;
        }
        bd.setOutputCost(calculateTokenCost(textOutputTokens, outputPrice).doubleValue());

        // 图片输出 token 费用（独立费率）
        if (tokens.getImageOutputTokens() > 0) {
            BigDecimal imgPrice = pricing.getImageOutputPricePerToken();
            if (imgPrice == null || imgPrice.compareTo(BigDecimal.ZERO) == 0) {
                imgPrice = outputPrice; // 回退到常规输出价格
            }
            bd.setImageOutputCost(calculateTokenCost(tokens.getImageOutputTokens(), imgPrice).doubleValue());
        }

        // 缓存创建费用
        bd.setCacheCreationCost(computeCacheCreationCost(pricing, tokens).doubleValue());

        // 缓存读取费用
        bd.setCacheReadCost(calculateTokenCost(tokens.getCacheReadTokens(), cacheReadPrice).doubleValue());

        if (tierMultiplier != 1.0) {
            bd.setInputCost(bd.getInputCost() * tierMultiplier);
            bd.setOutputCost(bd.getOutputCost() * tierMultiplier);
            bd.setImageOutputCost(bd.getImageOutputCost() * tierMultiplier);
            bd.setCacheCreationCost(bd.getCacheCreationCost() * tierMultiplier);
            bd.setCacheReadCost(bd.getCacheReadCost() * tierMultiplier);
        }

        bd.setTotalCost(bd.getInputCost() + bd.getOutputCost() + bd.getImageOutputCost()
                + bd.getCacheCreationCost() + bd.getCacheReadCost());
        bd.setActualCost(bd.getTotalCost() * rateMultiplier);

        return bd;
    }

    /**
     * 判断是否使用优先服务等级定价
     */
    private boolean usePriorityServiceTierPricing(String serviceTier, ModelPricing pricing) {
        if (serviceTier == null || pricing == null) {
            return false;
        }
        String normalizedTier = serviceTier.toLowerCase().trim();
        if (!"priority".equals(normalizedTier)) {
            return false;
        }
        return pricing.getInputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0
                || pricing.getOutputPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0
                || pricing.getCacheReadPricePerTokenPriority().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 计算缓存创建费用（支持 5m/1h 分类或标准计费）
     */
    private BigDecimal computeCacheCreationCost(ModelPricing pricing, UsageTokens tokens) {
        if (pricing.isSupportsCacheBreakdown()
                && (pricing.getCacheCreation5mPrice().compareTo(BigDecimal.ZERO) > 0
                    || pricing.getCacheCreation1hPrice().compareTo(BigDecimal.ZERO) > 0)) {
            if (tokens.getCacheCreation5mTokens() == 0
                    && tokens.getCacheCreation1hTokens() == 0
                    && tokens.getCacheCreationTokens() > 0) {
                // API 未返回 ephemeral 明细，回退到全部按 5m 单价计费
                return calculateTokenCost(tokens.getCacheCreationTokens(), pricing.getCacheCreation5mPrice());
            }
            return calculateTokenCost(tokens.getCacheCreation5mTokens(), pricing.getCacheCreation5mPrice())
                    .add(calculateTokenCost(tokens.getCacheCreation1hTokens(), pricing.getCacheCreation1hPrice()));
        }
        return calculateTokenCost(tokens.getCacheCreationTokens(), pricing.getCacheCreationPricePerToken());
    }

    /**
     * 判断是否应该应用长上下文定价
     */
    public boolean shouldApplySessionLongContextPricing(UsageTokens tokens, ModelPricing pricing) {
        if (pricing == null || pricing.getLongContextInputThreshold() <= 0) {
            return false;
        }
        if (pricing.getLongContextInputMultiplier().compareTo(BigDecimal.ONE) <= 0
                && pricing.getLongContextOutputMultiplier().compareTo(BigDecimal.ONE) <= 0) {
            return false;
        }
        int totalInputTokens = tokens.getInputTokens() + tokens.getCacheReadTokens();
        return totalInputTokens > pricing.getLongContextInputThreshold();
    }

    /**
     * 计算按次/图片计费费用
     *
     * @param unitPrice      单价
     * @param requestCount  请求数量
     * @param rateMultiplier 计费倍率
     * @param billingMode    计费模式
     * @return 费用明细
     */
    public CostBreakdown calculatePerRequestCost(double unitPrice, int requestCount,
                                                  double rateMultiplier, BillingMode billingMode) {
        if (requestCount <= 0) {
            requestCount = 1;
        }
        if (rateMultiplier <= 0) {
            rateMultiplier = 1.0;
        }

        double totalCost = unitPrice * requestCount;
        double actualCost = totalCost * rateMultiplier;

        CostBreakdown bd = new CostBreakdown();
        bd.setBillingMode(billingMode);
        bd.setTotalCost(totalCost);
        bd.setActualCost(actualCost);

        return bd;
    }

    /**
     * 计算图片生成费用
     *
     * @param model           模型名称
     * @param imageSize       图片尺寸 ("1K", "2K", "4K")
     * @param imageCount      图片数量
     * @param groupConfig     分组配置的价格（可能为 null）
     * @param rateMultiplier  计费倍率
     * @return 费用明细
     */
    public CostBreakdown calculateImageCost(String model, String imageSize, int imageCount,
                                             ImagePriceConfig groupConfig, double rateMultiplier) {
        if (imageCount <= 0) {
            return new CostBreakdown();
        }

        // 获取单价
        BigDecimal unitPrice = getImageUnitPrice(model, imageSize, groupConfig);

        // 计算总费用
        double totalCost = unitPrice.doubleValue() * imageCount;

        // 应用倍率
        if (rateMultiplier <= 0) {
            rateMultiplier = 1.0;
        }
        double actualCost = totalCost * rateMultiplier;

        CostBreakdown bd = new CostBreakdown();
        bd.setBillingMode(BillingMode.IMAGE);
        bd.setTotalCost(totalCost);
        bd.setActualCost(actualCost);

        return bd;
    }

    /**
     * 获取图片单价
     */
    private BigDecimal getImageUnitPrice(String model, String imageSize, ImagePriceConfig groupConfig) {
        // 优先使用分组配置的价格
        if (groupConfig != null) {
            switch (imageSize != null ? imageSize.toUpperCase() : "1K") {
                case "1K":
                    if (groupConfig.getPrice1K() != null) {
                        return groupConfig.getPrice1K();
                    }
                    break;
                case "2K":
                    if (groupConfig.getPrice2K() != null) {
                        return groupConfig.getPrice2K();
                    }
                    break;
                case "4K":
                    if (groupConfig.getPrice4K() != null) {
                        return groupConfig.getPrice4K();
                    }
                    break;
            }
        }

        // 回退到默认值 ($0.134)
        BigDecimal basePrice = new BigDecimal("0.134");

        // 2K 尺寸 1.5 倍，4K 尺寸翻倍
        if ("2K".equalsIgnoreCase(imageSize)) {
            return basePrice.multiply(new BigDecimal("1.5"));
        }
        if ("4K".equalsIgnoreCase(imageSize)) {
            return basePrice.multiply(new BigDecimal("2"));
        }

        return basePrice;
    }

    /**
     * 获取服务等级费用倍率
     */
    public double serviceTierCostMultiplier(String serviceTier) {
        if (serviceTier == null) {
            return 1.0;
        }
        switch (serviceTier.toLowerCase().trim()) {
            case "priority":
                return 2.0;
            case "flex":
                return 0.5;
            default:
                return 1.0;
        }
    }

    /**
     * 将 PricingService.ModelPricing 转换为 BillingCalculator.ModelPricing
     */
    public ModelPricing convertPricing(com.sub2api.module.billing.service.PricingService.ModelPricing litellmPricing) {
        if (litellmPricing == null) {
            return null;
        }

        ModelPricing pricing = new ModelPricing();
        pricing.setInputPricePerToken(BigDecimal.valueOf(litellmPricing.getInputCostPerToken()));
        pricing.setInputPricePerTokenPriority(BigDecimal.valueOf(litellmPricing.getInputCostPerTokenPriority()));
        pricing.setOutputPricePerToken(BigDecimal.valueOf(litellmPricing.getOutputCostPerToken()));
        pricing.setOutputPricePerTokenPriority(BigDecimal.valueOf(litellmPricing.getOutputCostPerTokenPriority()));
        pricing.setCacheCreationPricePerToken(BigDecimal.valueOf(litellmPricing.getCacheCreationInputTokenCost()));
        pricing.setCacheReadPricePerToken(BigDecimal.valueOf(litellmPricing.getCacheReadInputTokenCost()));
        pricing.setCacheReadPricePerTokenPriority(BigDecimal.valueOf(litellmPricing.getCacheReadInputTokenCostPriority()));
        pricing.setCacheCreation5mPrice(BigDecimal.valueOf(litellmPricing.getCacheCreationInputTokenCost()));
        pricing.setCacheCreation1hPrice(BigDecimal.valueOf(litellmPricing.getCacheCreationInputTokenCostAbove1hr()));

        // 启用 5m/1h 分类计费的条件：存在 1h 价格且 1h 价格 > 5m 价格
        double price5m = litellmPricing.getCacheCreationInputTokenCost();
        double price1h = litellmPricing.getCacheCreationInputTokenCostAbove1hr();
        boolean enableBreakdown = price1h > 0 && price1h > price5m;
        pricing.setSupportsCacheBreakdown(enableBreakdown);

        pricing.setLongContextInputThreshold(litellmPricing.getLongContextInputTokenThreshold());
        pricing.setLongContextInputMultiplier(BigDecimal.valueOf(litellmPricing.getLongContextInputCostMultiplier()));
        pricing.setLongContextOutputMultiplier(BigDecimal.valueOf(litellmPricing.getLongContextOutputCostMultiplier()));
        pricing.setImageOutputPricePerToken(BigDecimal.valueOf(litellmPricing.getOutputCostPerImageToken()));

        return pricing;
    }
}
