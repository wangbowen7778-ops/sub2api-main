package com.sub2api.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.billing.mapper.UsageLogMapper;
import com.sub2api.module.billing.model.entity.UsageLog;
import com.sub2api.module.common.model.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量日志服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageLogService extends ServiceImpl<UsageLogMapper, UsageLog> {

    private final UsageLogMapper usageLogMapper;
    private final BillingCalculator billingCalculator;

    /**
     * 记录用量
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordUsage(UsageLog usageLog) {
        // 计算费用
        Long cost = billingCalculator.calculateCost(
                usageLog.getInputTokens() != null ? usageLog.getInputTokens().longValue() : 0L,
                usageLog.getOutputTokens() != null ? usageLog.getOutputTokens().longValue() : 0L,
                usageLog.getRateMultiplier()
        );
        usageLog.setTotalCost(BigDecimal.valueOf(cost).divide(BigDecimal.valueOf(100)));
        usageLog.setActualCost(usageLog.getTotalCost());
        usageLog.setInputCost(BigDecimal.valueOf(cost).divide(BigDecimal.valueOf(100)));
        usageLog.setOutputCost(BigDecimal.ZERO);

        usageLog.setCreatedAt(LocalDateTime.now());

        if (!save(usageLog)) {
            log.error("记录用量失败: userId={}", usageLog.getUserId());
        }

        log.debug("记录用量: userId={}, inputTokens={}, outputTokens={}, cost={}",
                usageLog.getUserId(), usageLog.getInputTokens(), usageLog.getOutputTokens(), cost);
    }

    /**
     * 分页查询用户用量
     */
    public PageResult<UsageLog> pageByUserId(Long userId, Long current, Long size,
                                              String platform, String model, LocalDateTime startTime, LocalDateTime endTime) {
        Page<UsageLog> page = new Page<>(current, size);
        LambdaQueryWrapper<UsageLog> wrapper = new LambdaQueryWrapper<UsageLog>()
                .eq(UsageLog::getUserId, userId)
                .orderByDesc(UsageLog::getCreatedAt);

        // Note: platform filter is not supported by UsageLog entity (no platform field)
        if (StringUtils.hasText(model)) {
            wrapper.like(UsageLog::getModel, model);
        }
        if (startTime != null) {
            wrapper.ge(UsageLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(UsageLog::getCreatedAt, endTime);
        }

        Page<UsageLog> result = page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 统计用户总用量
     */
    public UsageStatistics getUserStatistics(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<UsageLog> wrapper = new LambdaQueryWrapper<UsageLog>()
                .eq(UsageLog::getUserId, userId);

        if (startTime != null) {
            wrapper.ge(UsageLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(UsageLog::getCreatedAt, endTime);
        }

        List<UsageLog> logs = list(wrapper);

        long totalInputTokens = logs.stream()
                .mapToLong(l -> l.getInputTokens() != null ? l.getInputTokens() : 0)
                .sum();
        long totalOutputTokens = logs.stream()
                .mapToLong(l -> l.getOutputTokens() != null ? l.getOutputTokens() : 0)
                .sum();
        BigDecimal totalCost = logs.stream()
                .map(l -> l.getTotalCost() != null ? l.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long requestCount = logs.size();

        return new UsageStatistics(totalInputTokens, totalOutputTokens, totalCost, requestCount);
    }

    /**
     * 统计 API Key 总用量
     */
    public UsageStatistics getApiKeyStatistics(Long apiKeyId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<UsageLog> wrapper = new LambdaQueryWrapper<UsageLog>()
                .eq(UsageLog::getApiKeyId, apiKeyId);

        if (startTime != null) {
            wrapper.ge(UsageLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(UsageLog::getCreatedAt, endTime);
        }

        List<UsageLog> logs = list(wrapper);

        long totalInputTokens = logs.stream()
                .mapToLong(l -> l.getInputTokens() != null ? l.getInputTokens() : 0)
                .sum();
        long totalOutputTokens = logs.stream()
                .mapToLong(l -> l.getOutputTokens() != null ? l.getOutputTokens() : 0)
                .sum();
        BigDecimal totalCost = logs.stream()
                .map(l -> l.getTotalCost() != null ? l.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long requestCount = logs.size();

        return new UsageStatistics(totalInputTokens, totalOutputTokens, totalCost, requestCount);
    }

    /**
     * 用量统计
     */
    public record UsageStatistics(long inputTokens, long outputTokens, BigDecimal cost, long requestCount) {
    }
}
