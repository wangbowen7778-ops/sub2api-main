package com.sub2api.module.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 计费计算服务
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
     * 计算 Token 费用
     *
     * @param tokens token 数量
     * @param ratePerMillion 每百万费率
     * @return 费用
     */
    private BigDecimal calculateTokenCost(Long tokens, BigDecimal ratePerMillion) {
        if (tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP)
                .multiply(ratePerMillion);
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
}
