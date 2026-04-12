package com.sub2api.module.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.mapper.AccountGroupMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.entity.Group;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分组容量服务
 * 聚合各分组的运行时容量数据
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCapacityService {

    private final AccountMapper accountMapper;
    private final AccountGroupMapper accountGroupMapper;
    private final GroupService groupService;
    private final ConcurrencyService concurrencyService;

    /**
     * 分组容量摘要
     */
    @Data
    public static class GroupCapacitySummary {
        private Long groupId;
        private int concurrencyUsed;
        private int concurrencyMax;
        private int sessionsUsed;
        private int sessionsMax;
        private int rpmUsed;
        private int rpmMax;
    }

    /**
     * 获取所有活跃分组的容量摘要
     */
    public List<GroupCapacitySummary> getAllGroupCapacity() {
        List<Group> groups = groupService.listActive();
        List<GroupCapacitySummary> results = new ArrayList<>();

        for (Group group : groups) {
            try {
                GroupCapacitySummary summary = getGroupCapacity(group.getId());
                summary.setGroupId(group.getId());
                results.add(summary);
            } catch (Exception e) {
                log.warn("Failed to get capacity for group {}: {}", group.getId(), e.getMessage());
                // Skip groups with errors, return partial results
            }
        }

        return results;
    }

    /**
     * 获取指定分组的容量
     */
    public GroupCapacitySummary getGroupCapacity(Long groupId) {
        // 获取分组下所有可调度账号
        List<Account> accounts = getSchedulableAccountsByGroup(groupId);
        if (accounts.isEmpty()) {
            GroupCapacitySummary empty = new GroupCapacitySummary();
            empty.setGroupId(groupId);
            return empty;
        }

        GroupCapacitySummary summary = new GroupCapacitySummary();
        summary.setGroupId(groupId);

        // 收集账号 ID 和配置值
        List<Long> accountIds = new ArrayList<>();
        int concurrencyMax = 0;
        int sessionsMax = 0;

        for (Account account : accounts) {
            accountIds.add(account.getId());

            // 累加最大并发
            if (account.getConcurrency() != null) {
                concurrencyMax += account.getConcurrency();
            }

            // 累加最大会话数
            Integer maxSessions = getMaxSessions(account);
            if (maxSessions != null && maxSessions > 0) {
                sessionsMax += maxSessions;
            }
        }

        // 批量查询运行时数据
        Map<Long, Integer> concurrencyMap = concurrencyService.getAccountConcurrencyBatch(accountIds);
        int concurrencyUsed = concurrencyMap.values().stream().mapToInt(Integer::intValue).sum();

        summary.setConcurrencyUsed(concurrencyUsed);
        summary.setConcurrencyMax(concurrencyMax);
        summary.setSessionsUsed(0); // TODO: 实现会话追踪
        summary.setSessionsMax(sessionsMax);
        summary.setRpmUsed(0); // TODO: 实现 RPM 追踪
        summary.setRpmMax(0);

        return summary;
    }

    /**
     * 获取分组下所有可调度的账号
     */
    private List<Account> getSchedulableAccountsByGroup(Long groupId) {
        // 通过 account_groups 表查找分组下的账号
        List<Long> accountIds = accountGroupMapper.selectAccountIdsByGroupId(groupId);
        if (accountIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询这些账号中状态为 active 且可调度的
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getId, accountIds)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true));
    }

    /**
     * 获取账号最大会话数
     */
    private Integer getMaxSessions(Account account) {
        if (account.getExtra() == null) {
            return null;
        }
        Object maxSessions = account.getExtra().get("max_sessions");
        if (maxSessions instanceof Number) {
            return ((Number) maxSessions).intValue();
        }
        return null;
    }

    /**
     * 获取分组使用率
     */
    public double getGroupUsagePercent(Long groupId) {
        GroupCapacitySummary summary = getGroupCapacity(groupId);
        if (summary.getConcurrencyMax() <= 0) {
            return 0;
        }
        return (double) summary.getConcurrencyUsed() / summary.getConcurrencyMax() * 100;
    }
}
