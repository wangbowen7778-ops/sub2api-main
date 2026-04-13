package com.sub2api.module.billing.service;

import com.sub2api.module.billing.service.BillingCalculator.BillingMode;
import com.sub2api.module.billing.service.BillingCalculator.CostBreakdown;
import com.sub2api.module.billing.service.BillingCalculator.ImagePriceConfig;
import com.sub2api.module.billing.service.BillingCalculator.ModelPricing;
import com.sub2api.module.billing.service.BillingCalculator.UsageTokens;
import com.sub2api.module.channel.model.entity.ChannelModelPricing;
import com.sub2api.module.channel.service.ChannelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BillingService - 综合计费服务
 * 提供统一的计费入口，支持动态定价、渠道价格覆盖、fallback 定价等
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final PricingService pricingService;
    private final BillingCalculator billingCalculator;
    private final ChannelService channelService;

    // 默认费率倍率 (当配置中未指定时使用)
    private static final double DEFAULT_RATE_MULTIPLIER = 1.0;

    // GPT-4o Long Context 阈值和倍率
    private static final int GPT54_LONG_CONTEXT_THRESHOLD = 272000;
    private static final double GPT54_LONG_CONTEXT_INPUT_MULTIPLIER = 2.0;
    private static final double GPT54_LONG_CONTEXT_OUTPUT_MULTIPLIER = 1.5;

    // Fallback 定价缓存
    private final Map<String, ModelPricing> fallbackPrices = new ConcurrentHashMap<>();

    /**
     * 统一计费入口
     */
    @Data
    public static class CostInput {
        private String model;
        private Long groupId;
        private UsageTokens tokens;
        private int requestCount;
        private String sizeTier;
        private double rateMultiplier;
        private String serviceTier;
        private ResolvedPricing resolved;
    }

    /**
     * 统一定价解析结果
     */
    @Data
    public static class ResolvedPricing {
        private BillingMode mode = BillingMode.TOKEN;
        private ModelPricing basePricing;
        private java.util.List<PricingIntervalData> intervals;
        private java.util.List<PricingIntervalData> requestTiers;
        private double defaultPerRequestPrice;
        private String source;
        private boolean supportsCacheBreakdown;
    }

    /**
     * 定价区间数据
     */
    @Data
    public static class PricingIntervalData {
        private int minTokens;
        private Integer maxTokens;
        private String tierLabel;
        private BigDecimal inputPrice;
        private BigDecimal outputPrice;
        private BigDecimal cacheWritePrice;
        private BigDecimal cacheReadPrice;
        private BigDecimal perRequestPrice;
    }

    /**
     * 初始化 fallback 定价
     */
    public BillingService(PricingService pricingService, BillingCalculator billingCalculator) {
        this(pricingService, billingCalculator, null);
        initFallbackPricing();
    }

    /**
     * 初始化 fallback 定价
     */
    private void initFallbackPricing() {
        // Claude 4.5 Opus
        fallbackPrices.put("claude-opus-4.5", createModelPricing(
                5e-6, 25e-6, 6.25e-6, 0.5e-6, false));

        // Claude 4 Sonnet
        fallbackPrices.put("claude-sonnet-4", createModelPricing(
                3e-6, 15e-6, 3.75e-6, 0.3e-6, false));

        // Claude 3.5 Sonnet
        fallbackPrices.put("claude-3-5-sonnet", createModelPricing(
                3e-6, 15e-6, 3.75e-6, 0.3e-6, false));

        // Claude 3.5 Haiku
        fallbackPrices.put("claude-3-5-haiku", createModelPricing(
                1e-6, 5e-6, 1.25e-6, 0.1e-6, false));

        // Claude 3 Opus
        fallbackPrices.put("claude-3-opus", createModelPricing(
                15e-6, 75e-6, 18.75e-6, 1.5e-6, false));

        // Claude 3 Haiku
        fallbackPrices.put("claude-3-haiku", createModelPricing(
                0.25e-6, 1.25e-6, 0.3e-6, 0.03e-6, false));

        // Claude 4.6 Opus (与4.5同价)
        fallbackPrices.put("claude-opus-4.6", fallbackPrices.get("claude-opus-4.5"));

        // Gemini 3.1 Pro
        fallbackPrices.put("gemini-3.1-pro", createModelPricing(
                2e-6, 12e-6, 2e-6, 0.2e-6, false));

        // OpenAI GPT-5.1
        fallbackPrices.put("gpt-5.1", createModelPricingWithPriority(
                1.25e-6, 2.5e-6, 10e-6, 20e-6, 1.25e-6, 0.125e-6, 0.25e-6, false));

        // OpenAI GPT-5.4
        ModelPricing gpt54 = createModelPricingWithPriority(
                2.5e-6, 5e-6, 15e-6, 30e-6, 2.5e-6, 0.25e-6, 0.5e-6, false);
        gpt54.setLongContextInputThreshold(GPT54_LONG_CONTEXT_THRESHOLD);
        gpt54.setLongContextInputMultiplier(BigDecimal.valueOf(GPT54_LONG_CONTEXT_INPUT_MULTIPLIER));
        gpt54.setLongContextOutputMultiplier(BigDecimal.valueOf(GPT54_LONG_CONTEXT_OUTPUT_MULTIPLIER));
        fallbackPrices.put("gpt-5.4", gpt54);

        // GPT-5.4-mini
        fallbackPrices.put("gpt-5.4-mini", createModelPricing(
                7.5e-7, 4.5e-6, 0, 7.5e-8, false));

        // GPT-5.4-nano
        fallbackPrices.put("gpt-5.4-nano", createModelPricing(
                2e-7, 1.25e-6, 0, 2e-8, false));

        // OpenAI GPT-5.2
        fallbackPrices.put("gpt-5.2", createModelPricingWithPriority(
                1.75e-6, 3.5e-6, 14e-6, 28e-6, 1.75e-6, 0.175e-6, 0.35e-6, false));

        // Codex 族
        fallbackPrices.put("gpt-5.1-codex", createModelPricingWithPriority(
                1.5e-6, 3e-6, 12e-6, 24e-6, 1.5e-6, 0.15e-6, 0.3e-6, false));

        fallbackPrices.put("gpt-5.2-codex", createModelPricingWithPriority(
                1.75e-6, 3.5e-6, 14e-6, 28e-6, 1.75e-6, 0.175e-6, 0.35e-6, false));

        fallbackPrices.put("gpt-5.3-codex", fallbackPrices.get("gpt-5.1-codex"));
    }

    private ModelPricing createModelPricing(double input, double output, double cacheCreation, double cacheRead, boolean supportsCacheBreakdown) {
        ModelPricing p = new ModelPricing();
        p.setInputPricePerToken(BigDecimal.valueOf(input));
        p.setOutputPricePerToken(BigDecimal.valueOf(output));
        p.setCacheCreationPricePerToken(BigDecimal.valueOf(cacheCreation));
        p.setCacheReadPricePerToken(BigDecimal.valueOf(cacheRead));
        p.setSupportsCacheBreakdown(supportsCacheBreakdown);
        return p;
    }

    private ModelPricing createModelPricingWithPriority(double input, double inputPriority,
                                                        double output, double outputPriority,
                                                        double cacheCreation, double cacheRead, double cacheReadPriority,
                                                        boolean supportsCacheBreakdown) {
        ModelPricing p = createModelPricing(input, output, cacheCreation, cacheRead, supportsCacheBreakdown);
        p.setInputPricePerTokenPriority(BigDecimal.valueOf(inputPriority));
        p.setOutputPricePerTokenPriority(BigDecimal.valueOf(outputPriority));
        p.setCacheReadPricePerTokenPriority(BigDecimal.valueOf(cacheReadPriority));
        return p;
    }

    /**
     * 获取模型定价（优先动态定价，fallback 到硬编码）
     */
    public ModelPricing getModelPricing(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }

        String normalizedModel = model.toLowerCase().trim();

        // 1. 优先从动态价格服务获取
        PricingService.ModelPricing litellmPricing = pricingService.getModelPricing(normalizedModel);
        if (litellmPricing != null) {
            ModelPricing pricing = billingCalculator.convertPricing(litellmPricing);
            if (pricing != null) {
                return applyModelSpecificPricingPolicy(normalizedModel, pricing);
            }
        }

        // 2. 使用硬编码 fallback 价格
        ModelPricing fallback = getFallbackPricing(normalizedModel);
        if (fallback != null) {
            log.debug("Using fallback pricing for model: {}", model);
            return applyModelSpecificPricingPolicy(normalizedModel, fallback);
        }

        return null;
    }

    /**
     * 获取模型定价（带渠道价格覆盖）
     */
    public ModelPricing getModelPricingWithChannel(String model, Long groupId, ChannelModelPricing channelPricing) {
        ModelPricing pricing = getModelPricing(model);
        if (pricing == null) {
            return null;
        }

        if (channelPricing == null) {
            return pricing;
        }

        // 应用渠道价格覆盖
        if (channelPricing.getInputPrice() != null) {
            BigDecimal inputPrice = channelPricing.getInputPrice();
            pricing.setInputPricePerToken(inputPrice);
            pricing.setInputPricePerTokenPriority(inputPrice);
        }
        if (channelPricing.getOutputPrice() != null) {
            BigDecimal outputPrice = channelPricing.getOutputPrice();
            pricing.setOutputPricePerToken(outputPrice);
            pricing.setOutputPricePerTokenPriority(outputPrice);
        }
        if (channelPricing.getCacheWritePrice() != null) {
            BigDecimal cacheWritePrice = channelPricing.getCacheWritePrice();
            pricing.setCacheCreationPricePerToken(cacheWritePrice);
            pricing.setCacheCreation5mPrice(cacheWritePrice);
            pricing.setCacheCreation1hPrice(cacheWritePrice);
        }
        if (channelPricing.getCacheReadPrice() != null) {
            BigDecimal cacheReadPrice = channelPricing.getCacheReadPrice();
            pricing.setCacheReadPricePerToken(cacheReadPrice);
            pricing.setCacheReadPricePerTokenPriority(cacheReadPrice);
        }
        if (channelPricing.getImageOutputPrice() != null) {
            pricing.setImageOutputPricePerToken(channelPricing.getImageOutputPrice());
        }

        return pricing;
    }

    /**
     * 获取 Fallback 定价
     */
    private ModelPricing getFallbackPricing(String model) {
        String modelLower = model.toLowerCase();

        // Claude 系列匹配
        if (modelLower.contains("opus")) {
            if (modelLower.contains("4.6") || modelLower.contains("4-6")) {
                return fallbackPrices.get("claude-opus-4.6");
            }
            if (modelLower.contains("4.5") || modelLower.contains("4-5")) {
                return fallbackPrices.get("claude-opus-4.5");
            }
            return fallbackPrices.get("claude-3-opus");
        }
        if (modelLower.contains("sonnet")) {
            if (modelLower.contains("4") && !modelLower.contains("3")) {
                return fallbackPrices.get("claude-sonnet-4");
            }
            return fallbackPrices.get("claude-3-5-sonnet");
        }
        if (modelLower.contains("haiku")) {
            if (modelLower.contains("3-5") || modelLower.contains("3.5")) {
                return fallbackPrices.get("claude-3-5-haiku");
            }
            return fallbackPrices.get("claude-3-haiku");
        }
        // Claude 未知型号统一回退到 Sonnet
        if (modelLower.contains("claude")) {
            return fallbackPrices.get("claude-sonnet-4");
        }

        // Gemini 匹配
        if (modelLower.contains("gemini-3.1-pro") || modelLower.contains("gemini-3-1-pro")) {
            return fallbackPrices.get("gemini-3.1-pro");
        }

        // OpenAI GPT/Codex 匹配
        if (modelLower.contains("gpt-5") || modelLower.contains("codex")) {
            String normalized = normalizeCodexModel(modelLower);
            return switch (normalized) {
                case "gpt-5.4-mini" -> fallbackPrices.get("gpt-5.4-mini");
                case "gpt-5.4-nano" -> fallbackPrices.get("gpt-5.4-nano");
                case "gpt-5.4" -> fallbackPrices.get("gpt-5.4");
                case "gpt-5.2" -> fallbackPrices.get("gpt-5.2");
                case "gpt-5.2-codex" -> fallbackPrices.get("gpt-5.2-codex");
                case "gpt-5.3-codex" -> fallbackPrices.get("gpt-5.3-codex");
                case "gpt-5.1-codex", "gpt-5.1-codex-max", "gpt-5.1-codex-mini", "codex-mini-latest" -> fallbackPrices.get("gpt-5.1-codex");
                case "gpt-5.1" -> fallbackPrices.get("gpt-5.1");
                default -> fallbackPrices.get("gpt-5.1");
            };
        }

        return null;
    }

    /**
     * 标准化 Codex 模型名称
     */
    private String normalizeCodexModel(String model) {
        if (model == null || model.isBlank()) {
            return "gpt-5.1";
        }

        if (model.contains("/")) {
            String[] parts = model.split("/");
            model = parts[parts.length - 1];
        }

        model = model.toLowerCase().trim();

        if (model.contains("gpt-5.4-mini") || model.contains("gpt 5.4 mini")) {
            return "gpt-5.4-mini";
        }
        if (model.contains("gpt-5.4-nano") || model.contains("gpt 5.4 nano")) {
            return "gpt-5.4-nano";
        }
        if (model.contains("gpt-5.4") || model.contains("gpt 5.4")) {
            return "gpt-5.4";
        }
        if (model.contains("gpt-5.2-codex") || model.contains("gpt 5.2 codex")) {
            return "gpt-5.2-codex";
        }
        if (model.contains("gpt-5.2") || model.contains("gpt 5.2")) {
            return "gpt-5.2";
        }
        if (model.contains("gpt-5.3-codex") || model.contains("gpt 5.3 codex")) {
            return "gpt-5.3-codex";
        }
        if (model.contains("gpt-5.3") || model.contains("gpt 5.3")) {
            return "gpt-5.3-codex";
        }
        if (model.contains("gpt-5.1-codex-max") || model.contains("gpt 5.1 codex max")) {
            return "gpt-5.1-codex-max";
        }
        if (model.contains("gpt-5.1-codex") || model.contains("gpt 5.1 codex")) {
            return "gpt-5.1-codex";
        }
        if (model.contains("codex-mini-latest")) {
            return "gpt-5.1-codex";
        }
        if (model.contains("gpt-5.1") || model.contains("gpt 5.1")) {
            return "gpt-5.1";
        }

        return model;
    }

    /**
     * 应用模型特定定价策略
     */
    private ModelPricing applyModelSpecificPricingPolicy(String model, ModelPricing pricing) {
        if (pricing == null) {
            return null;
        }
        if (!isOpenAIGPT54Model(model)) {
            return pricing;
        }
        if (pricing.getLongContextInputThreshold() > 0
                && pricing.getLongContextInputMultiplier().compareTo(BigDecimal.ZERO) > 0
                && pricing.getLongContextOutputMultiplier().compareTo(BigDecimal.ZERO) > 0) {
            return pricing;
        }

        // Clone and apply defaults for GPT-5.4
        ModelPricing cloned = clonePricing(pricing);
        if (cloned.getLongContextInputThreshold() <= 0) {
            cloned.setLongContextInputThreshold(GPT54_LONG_CONTEXT_THRESHOLD);
        }
        if (cloned.getLongContextInputMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            cloned.setLongContextInputMultiplier(BigDecimal.valueOf(GPT54_LONG_CONTEXT_INPUT_MULTIPLIER));
        }
        if (cloned.getLongContextOutputMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            cloned.setLongContextOutputMultiplier(BigDecimal.valueOf(GPT54_LONG_CONTEXT_OUTPUT_MULTIPLIER));
        }
        return cloned;
    }

    private boolean isOpenAIGPT54Model(String model) {
        String normalized = normalizeCodexModel(model.trim().toLowerCase());
        return "gpt-5.4".equals(normalized);
    }

    private ModelPricing clonePricing(ModelPricing original) {
        ModelPricing clone = new ModelPricing();
        clone.setInputPricePerToken(original.getInputPricePerToken());
        clone.setInputPricePerTokenPriority(original.getInputPricePerTokenPriority());
        clone.setOutputPricePerToken(original.getOutputPricePerToken());
        clone.setOutputPricePerTokenPriority(original.getOutputPricePerTokenPriority());
        clone.setCacheCreationPricePerToken(original.getCacheCreationPricePerToken());
        clone.setCacheReadPricePerToken(original.getCacheReadPricePerToken());
        clone.setCacheReadPricePerTokenPriority(original.getCacheReadPricePerTokenPriority());
        clone.setCacheCreation5mPrice(original.getCacheCreation5mPrice());
        clone.setCacheCreation1hPrice(original.getCacheCreation1hPrice());
        clone.setSupportsCacheBreakdown(original.isSupportsCacheBreakdown());
        clone.setLongContextInputThreshold(original.getLongContextInputThreshold());
        clone.setLongContextInputMultiplier(original.getLongContextInputMultiplier());
        clone.setLongContextOutputMultiplier(original.getLongContextOutputMultiplier());
        clone.setImageOutputPricePerToken(original.getImageOutputPricePerToken());
        return clone;
    }

    /**
     * 统一计费入口
     */
    public CostBreakdown calculateCostUnified(CostInput input) {
        if (input.getRateMultiplier() <= 0) {
            input.setRateMultiplier(DEFAULT_RATE_MULTIPLIER);
        }

        ModelPricing pricing;
        if (input.getResolved() != null && input.getResolved().getBasePricing() != null) {
            pricing = input.getResolved().getBasePricing();
        } else if (input.getGroupId() != null) {
            ChannelModelPricing channelPricing = channelService.getChannelModelPricing(input.getGroupId(), input.getModel());
            pricing = getModelPricingWithChannel(input.getModel(), input.getGroupId(), channelPricing);
        } else {
            pricing = getModelPricing(input.getModel());
        }

        if (pricing == null) {
            log.warn("No pricing found for model: {}", input.getModel());
            return new CostBreakdown();
        }

        // 根据计费模式分发计算
        BillingMode mode = BillingMode.TOKEN;
        if (input.getResolved() != null) {
            mode = input.getResolved().getMode();
        }

        return switch (mode) {
            case PER_REQUEST -> calculatePerRequestCostInternal(input, pricing);
            case IMAGE -> calculateImageCostInternal(input, pricing);
            default -> calculateTokenCostInternal(input, pricing);
        };
    }

    private CostBreakdown calculateTokenCostInternal(CostInput input, ModelPricing pricing) {
        if (input.getTokens() == null) {
            input.setTokens(new UsageTokens());
        }

        // 检查是否应用长上下文定价
        boolean applyLongCtx = !hasIntervalPricing(input.getResolved());
        return billingCalculator.computeTokenBreakdown(
                pricing,
                input.getTokens(),
                input.getRateMultiplier(),
                input.getServiceTier(),
                applyLongCtx
        );
    }

    private CostBreakdown calculatePerRequestCostInternal(CostInput input, ModelPricing pricing) {
        int count = input.getRequestCount();
        if (count <= 0) {
            count = 1;
        }

        double unitPrice = 0;

        // 优先使用层级价格
        if (input.getSizeTier() != null && !input.getSizeTier().isBlank()
                && input.getResolved() != null && input.getResolved().getRequestTiers() != null) {
            unitPrice = getRequestTierPrice(input.getResolved().getRequestTiers(), input.getSizeTier());
        }

        // 回退到按上下文查找
        if (unitPrice == 0 && input.getResolved() != null) {
            int totalContext = 0;
            if (input.getTokens() != null) {
                totalContext = input.getTokens().getInputTokens() + input.getTokens().getCacheReadTokens();
            }
            unitPrice = getRequestTierPriceByContext(input.getResolved().getRequestTiers(), totalContext);
        }

        // 回退到默认按次价格
        if (unitPrice == 0 && input.getResolved() != null) {
            unitPrice = input.getResolved().getDefaultPerRequestPrice();
        }

        return billingCalculator.calculatePerRequestCost(unitPrice, count, input.getRateMultiplier(), BillingMode.PER_REQUEST);
    }

    private CostBreakdown calculateImageCostInternal(CostInput input, ModelPricing pricing) {
        int count = input.getRequestCount();
        if (count <= 0) {
            count = 1;
        }

        double unitPrice = 0;

        // 优先使用层级价格
        if (input.getSizeTier() != null && !input.getSizeTier().isBlank()
                && input.getResolved() != null && input.getResolved().getRequestTiers() != null) {
            unitPrice = getRequestTierPrice(input.getResolved().getRequestTiers(), input.getSizeTier());
        }

        // 回退到按上下文查找
        if (unitPrice == 0 && input.getResolved() != null) {
            int totalContext = 0;
            if (input.getTokens() != null) {
                totalContext = input.getTokens().getInputTokens() + input.getTokens().getCacheReadTokens();
            }
            unitPrice = getRequestTierPriceByContext(input.getResolved().getRequestTiers(), totalContext);
        }

        // 回退到模型图片价格
        if (unitPrice == 0) {
            unitPrice = pricing.getImageOutputPricePerToken().doubleValue();
            if (unitPrice == 0) {
                unitPrice = 0.134; // 默认图片价格
            }
        }

        return billingCalculator.calculatePerRequestCost(unitPrice, count, input.getRateMultiplier(), BillingMode.IMAGE);
    }

    private boolean hasIntervalPricing(ResolvedPricing resolved) {
        return resolved != null && resolved.getIntervals() != null && !resolved.getIntervals().isEmpty();
    }

    private double getRequestTierPrice(java.util.List<PricingIntervalData> tiers, String tierLabel) {
        if (tiers == null || tierLabel == null) {
            return 0;
        }
        String labelLower = tierLabel.toLowerCase();
        for (PricingIntervalData tier : tiers) {
            if (tier.getTierLabel() != null && tier.getTierLabel().toLowerCase().equals(labelLower)
                    && tier.getPerRequestPrice() != null) {
                return tier.getPerRequestPrice().doubleValue();
            }
        }
        return 0;
    }

    private double getRequestTierPriceByContext(java.util.List<PricingIntervalData> tiers, int totalContext) {
        if (tiers == null || tiers.isEmpty()) {
            return 0;
        }
        for (PricingIntervalData tier : tiers) {
            if (totalContext > tier.getMinTokens()
                    && (tier.getMaxTokens() == null || totalContext <= tier.getMaxTokens())) {
                if (tier.getPerRequestPrice() != null) {
                    return tier.getPerRequestPrice().doubleValue();
                }
            }
        }
        return 0;
    }

    /**
     * 计算 Token 费用
     */
    public CostBreakdown calculateCost(String model, UsageTokens tokens, double rateMultiplier) {
        return calculateCostInternal(model, null, tokens, rateMultiplier, "", null);
    }

    /**
     * 计算 Token 费用（带服务等级）
     */
    public CostBreakdown calculateCostWithServiceTier(String model, UsageTokens tokens, double rateMultiplier, String serviceTier) {
        return calculateCostInternal(model, null, tokens, rateMultiplier, serviceTier, null);
    }

    /**
     * 计算 Token 费用（带渠道定价）
     */
    public CostBreakdown calculateCostInternal(String model, Long groupId, UsageTokens tokens,
                                               double rateMultiplier, String serviceTier,
                                               ChannelModelPricing channelPricing) {
        ModelPricing pricing;
        if (channelPricing != null) {
            pricing = getModelPricingWithChannel(model, groupId, channelPricing);
        } else if (groupId != null) {
            ChannelModelPricing ch = channelService.getChannelModelPricing(groupId, model);
            pricing = getModelPricingWithChannel(model, groupId, ch);
        } else {
            pricing = getModelPricing(model);
        }

        if (pricing == null) {
            log.warn("No pricing found for model: {}", model);
            return new CostBreakdown();
        }

        return billingCalculator.computeTokenBreakdown(pricing, tokens, rateMultiplier, serviceTier, true);
    }

    /**
     * 计算长上下文费用
     */
    public CostBreakdown calculateCostWithLongContext(String model, UsageTokens tokens,
                                                      double rateMultiplier, int threshold,
                                                      double extraMultiplier) {
        // 未启用长上下文计费，直接走正常计费
        if (threshold <= 0 || extraMultiplier <= 1) {
            return calculateCost(model, tokens, rateMultiplier);
        }

        // 计算总输入 token（缓存读取 + 新输入）
        int total = tokens.getCacheReadTokens() + tokens.getInputTokens();
        if (total <= threshold) {
            return calculateCost(model, tokens, rateMultiplier);
        }

        // 拆分成范围内和范围外
        int inRangeCacheTokens, inRangeInputTokens;
        int outRangeCacheTokens, outRangeInputTokens;

        if (tokens.getCacheReadTokens() >= threshold) {
            // 缓存已超过阈值：范围内只有缓存，范围外是超出的缓存+全部输入
            inRangeCacheTokens = threshold;
            inRangeInputTokens = 0;
            outRangeCacheTokens = tokens.getCacheReadTokens() - threshold;
            outRangeInputTokens = tokens.getInputTokens();
        } else {
            // 缓存未超过阈值：范围内是全部缓存+部分输入，范围外是剩余输入
            inRangeCacheTokens = tokens.getCacheReadTokens();
            inRangeInputTokens = threshold - tokens.getCacheReadTokens();
            outRangeCacheTokens = 0;
            outRangeInputTokens = tokens.getInputTokens() - inRangeInputTokens;
        }

        // 范围内部分：正常计费
        UsageTokens inRangeTokens = new UsageTokens();
        inRangeTokens.setInputTokens(inRangeInputTokens);
        inRangeTokens.setOutputTokens(tokens.getOutputTokens());
        inRangeTokens.setCacheCreationTokens(tokens.getCacheCreationTokens());
        inRangeTokens.setCacheReadTokens(inRangeCacheTokens);
        inRangeTokens.setCacheCreation5mTokens(tokens.getCacheCreation5mTokens());
        inRangeTokens.setCacheCreation1hTokens(tokens.getCacheCreation1hTokens());
        inRangeTokens.setImageOutputTokens(tokens.getImageOutputTokens());

        CostBreakdown inRangeCost = calculateCost(model, inRangeTokens, rateMultiplier);

        // 范围外部分：× extraMultiplier 计费
        UsageTokens outRangeTokens = new UsageTokens();
        outRangeTokens.setInputTokens(outRangeInputTokens);
        outRangeTokens.setCacheReadTokens(outRangeCacheTokens);

        CostBreakdown outRangeCost = calculateCost(model, outRangeTokens, rateMultiplier * extraMultiplier);

        // 合并成本
        CostBreakdown merged = new CostBreakdown();
        merged.setInputCost(inRangeCost.getInputCost() + outRangeCost.getInputCost());
        merged.setOutputCost(inRangeCost.getOutputCost());
        merged.setImageOutputCost(inRangeCost.getImageOutputCost());
        merged.setCacheCreationCost(inRangeCost.getCacheCreationCost());
        merged.setCacheReadCost(inRangeCost.getCacheReadCost() + outRangeCost.getCacheReadCost());
        merged.setTotalCost(inRangeCost.getTotalCost() + outRangeCost.getTotalCost());
        merged.setActualCost(inRangeCost.getActualCost() + outRangeCost.getActualCost());
        merged.setBillingMode(BillingMode.TOKEN);

        return merged;
    }

    /**
     * 计算图片生成费用
     */
    public CostBreakdown calculateImageCost(String model, String imageSize, int imageCount,
                                            ImagePriceConfig groupConfig, double rateMultiplier) {
        return billingCalculator.calculateImageCost(model, imageSize, imageCount, groupConfig, rateMultiplier);
    }

    /**
     * 估算费用（用于前端展示）
     */
    public double getEstimatedCost(String model, int estimatedInputTokens, int estimatedOutputTokens) {
        UsageTokens tokens = new UsageTokens();
        tokens.setInputTokens(estimatedInputTokens);
        tokens.setOutputTokens(estimatedOutputTokens);

        CostBreakdown breakdown = calculateCost(model, tokens, DEFAULT_RATE_MULTIPLIER);
        return breakdown.getActualCost();
    }

    /**
     * 获取价格服务状态
     */
    public Map<String, Object> getPricingServiceStatus() {
        return pricingService.getStatus();
    }

    /**
     * 强制更新价格数据
     */
    public boolean forceUpdatePricing() {
        return pricingService.forceUpdate();
    }

    /**
     * 列出所有支持的模型
     */
    public java.util.List<String> listSupportedModels() {
        return new java.util.ArrayList<>(fallbackPrices.keySet());
    }

    /**
     * 检查模型是否支持
     */
    public boolean isModelSupported(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String modelLower = model.toLowerCase();
        return modelLower.contains("claude")
                || modelLower.contains("opus")
                || modelLower.contains("sonnet")
                || modelLower.contains("haiku");
    }
}
