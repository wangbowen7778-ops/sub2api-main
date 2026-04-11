package com.sub2api.module.admin.service;

import com.sub2api.module.admin.mapper.ScheduledTestPlanMapper;
import com.sub2api.module.admin.mapper.ScheduledTestResultMapper;
import com.sub2api.module.admin.model.entity.ScheduledTestPlan;
import com.sub2api.module.admin.model.entity.ScheduledTestResult;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;

/**
 * 定时测试服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTestService {

    private final ScheduledTestPlanMapper planMapper;
    private final ScheduledTestResultMapper resultMapper;

    /**
     * 创建测试计划
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTestPlan createPlan(ScheduledTestPlan plan) {
        // 验证 cron 表达式
        LocalDateTime nextRun = computeNextRun(plan.getCronExpression());
        if (nextRun == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的 cron 表达式");
        }

        plan.setNextRunAt(nextRun);
        if (plan.getMaxResults() == null || plan.getMaxResults() <= 0) {
            plan.setMaxResults(50);
        }
        if (plan.getStatus() == null) {
            plan.setStatus("active");
        }
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());

        planMapper.insert(plan);
        log.info("创建定时测试计划: id={}, name={}", plan.getId(), plan.getName());
        return plan;
    }

    /**
     * 获取计划
     */
    public ScheduledTestPlan getPlan(Long id) {
        ScheduledTestPlan plan = planMapper.selectById(id);
        if (plan == null || plan.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "计划不存在");
        }
        return plan;
    }

    /**
     * 查询账号的所有计划
     */
    public List<ScheduledTestPlan> getPlansByAccount(Long accountId) {
        return planMapper.selectByAccountId(accountId);
    }

    /**
     * 更新计划
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTestPlan updatePlan(ScheduledTestPlan plan) {
        ScheduledTestPlan existing = getPlan(plan.getId());

        // 验证 cron 表达式
        LocalDateTime nextRun = computeNextRun(plan.getCronExpression());
        if (nextRun == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的 cron 表达式");
        }

        existing.setName(plan.getName());
        existing.setCronExpression(plan.getCronExpression());
        existing.setModel(plan.getModel());
        existing.setPrompt(plan.getPrompt());
        existing.setMaxResults(plan.getMaxResults());
        existing.setStatus(plan.getStatus());
        existing.setNextRunAt(nextRun);
        existing.setUpdatedAt(LocalDateTime.now());

        planMapper.updateById(existing);
        log.info("更新定时测试计划: id={}", existing.getId());
        return existing;
    }

    /**
     * 删除计划
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long id) {
        ScheduledTestPlan plan = getPlan(id);
        plan.setDeletedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        // 删除关联的结果
        resultMapper.deleteByPlanId(id);
        log.info("删除定时测试计划: id={}", id);
    }

    /**
     * 查询待执行的计划
     */
    public List<ScheduledTestPlan> getPendingPlans() {
        return planMapper.selectPendingPlans();
    }

    /**
     * 获取计划的结果
     */
    public List<ScheduledTestResult> getResults(Long planId, int limit) {
        if (limit <= 0) {
            limit = 50;
        }
        return resultMapper.selectByPlanId(planId, limit);
    }

    /**
     * 保存测试结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(Long planId, ScheduledTestResult result) {
        ScheduledTestPlan plan = getPlan(planId);
        result.setPlanId(planId);
        result.setExecutedAt(LocalDateTime.now());
        result.setCreatedAt(LocalDateTime.now());

        resultMapper.insert(result);

        // 更新计划的下次执行时间和最近执行时间
        LocalDateTime nextRun = computeNextRun(plan.getCronExpression());
        plan.setNextRunAt(nextRun);
        plan.setLastRunAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        // 修剪旧结果
        pruneOldResults(planId, plan.getMaxResults());
    }

    /**
     * 修剪旧结果
     */
    private void pruneOldResults(Long planId, int maxResults) {
        List<ScheduledTestResult> results = resultMapper.selectByPlanId(planId, Integer.MAX_VALUE);
        if (results.size() > maxResults) {
            // 删除超出限制的旧结果
            List<ScheduledTestResult> toDelete = results.subList(maxResults, results.size());
            for (ScheduledTestResult result : toDelete) {
                resultMapper.deleteById(result.getId());
            }
        }
    }

    /**
     * 暂停计划
     */
    @Transactional(rollbackFor = Exception.class)
    public void pausePlan(Long id) {
        ScheduledTestPlan plan = getPlan(id);
        plan.setStatus("paused");
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        log.info("暂停定时测试计划: id={}", id);
    }

    /**
     * 恢复计划
     */
    @Transactional(rollbackFor = Exception.class)
    public void resumePlan(Long id) {
        ScheduledTestPlan plan = getPlan(id);
        plan.setStatus("active");
        plan.setNextRunAt(computeNextRun(plan.getCronExpression()));
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        log.info("恢复定时测试计划: id={}", id);
    }

    /**
     * 计算下次执行时间
     */
    private LocalDateTime computeNextRun(String cronExpression) {
        try {
            // 简化实现: 解析 cron 表达式
            // 格式: 秒 分 时 日 月 周
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length < 5) {
                return null;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            // 简单解析: 设置到下一分钟
            int minute = cal.get(Calendar.MINUTE) + 1;
            cal.set(Calendar.MINUTE, minute);

            if (minute >= 60) {
                cal.add(Calendar.HOUR_OF_DAY, 1);
                cal.set(Calendar.MINUTE, 0);
            }

            return LocalDateTime.ofInstant(cal.toInstant(), java.time.ZoneId.systemDefault());
        } catch (Exception e) {
            log.warn("解析 cron 表达式失败: {}", cronExpression);
            return null;
        }
    }
}
