package com.sub2api.module.account.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.account.mapper.AccountGroupMapper;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.mapper.GroupMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.entity.Group;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 调度快照服务
 * 负责账号调度快照的管理和更新
 *
 * @author Sub2API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerSnapshotService {

    private final SchedulerCache schedulerCache;
    private final SchedulerOutboxRepository schedulerOutboxRepository;
    private final AccountMapper accountMapper;
    private final AccountGroupMapper accountGroupMapper;
    private final GroupMapper groupMapper;

    // Fallback 限流器
    private final AtomicInteger fallbackFailures = new AtomicInteger(0);
    private volatile boolean outboxWorkerRunning = false;
    private volatile boolean fullRebuildWorkerRunning = false;

    // Outbox 事件超时
    private static final Duration OUTBOX_EVENT_TIMEOUT = Duration.ofMinutes(2);
    // Bucket 重建锁 TTL
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    // Bucket 重建超时
    private static final Duration REBUILD_TIMEOUT = Duration.ofSeconds(30);
    // 初始重建超时
    private static final Duration INITIAL_REBUILD_TIMEOUT = Duration.ofMinutes(2);
    // 完全重建超时
    private static final Duration FULL_REBUILD_TIMEOUT = Duration.ofMinutes(2);

    // 配置项 (可以通过配置文件注入)
    private volatile boolean dbFallbackEnabled = true;
    private volatile int dbFallbackMaxQps = 100;
    private volatile int dbFallbackTimeoutSeconds = 10;
    private volatile int outboxPollIntervalSeconds = 10;
    private volatile int fullRebuildIntervalSeconds = 3600; // 默认1小时
    private volatile int outboxLagWarnSeconds = 60;
    private volatile int outboxLagRebuildSeconds = 300;
    private volatile int outboxLagRebuildFailures = 3;
    private volatile int outboxBacklogRebuildRows = 10000;

    // 平台常量
    private static final String PLATFORM_ANTHROPIC = "anthropic";
    private static final String PLATFORM_GEMINI = "gemini";
    private static final String PLATFORM_OPENAI = "openai";
    private static final String PLATFORM_ANTIGRAVITY = "antigravity";

    // 运行模式
    private static final String RUN_MODE_SIMPLE = "simple";

    @PostConstruct
    public void start() {
        // 初始重建
        runInitialRebuild();
    }

    @PreDestroy
    public void stop() {
        outboxWorkerRunning = false;
        fullRebuildWorkerRunning = false;
        log.info("[Scheduler] SchedulerSnapshotService stopped");
    }

    /**
     * 列出可调度的账号
     *
     * @param groupId         分组 ID（可选）
     * @param platform        平台
     * @param hasForcePlatform 是否有强制平台
     * @return 可调度账号列表和是否使用混合模式
     */
    public List<Account> listSchedulableAccounts(Long groupId, String platform, boolean hasForcePlatform) {
        boolean useMixed = (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) && !hasForcePlatform;
        String mode = resolveMode(platform, hasForcePlatform);
        SchedulerBucket bucket = bucketFor(groupId, platform, mode);

        // 尝试从缓存读取
        if (schedulerCache != null) {
            try {
                SchedulerCache.GetSnapshotResult result = schedulerCache.getSnapshot(bucket);
                if (result != null && result.hit() && result.accounts() != null) {
                    return result.accounts();
                }
            } catch (Exception e) {
                log.warn("[Scheduler] cache read failed: bucket={} err={}", bucket, e.getMessage());
            }
        }

        // 检查 fallback 是否允许
        if (!guardFallback()) {
            log.warn("[Scheduler] db fallback limited, returning empty");
            return Collections.emptyList();
        }

        // 从数据库加载
        List<Account> accounts = loadAccountsFromDB(bucket, useMixed);

        // 写入缓存
        if (schedulerCache != null && !accounts.isEmpty()) {
            try {
                schedulerCache.setSnapshot(bucket, accounts);
            } catch (Exception e) {
                log.warn("[Scheduler] cache write failed: bucket={} err={}", bucket, e.getMessage());
            }
        }

        return accounts;
    }

    /**
     * 获取单个账号
     */
    public Account getAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            return null;
        }

        if (schedulerCache != null) {
            try {
                Account account = schedulerCache.getAccount(accountId);
                if (account != null) {
                    return account;
                }
            } catch (Exception e) {
                log.warn("[Scheduler] account cache read failed: id={} err={}", accountId, e.getMessage());
            }
        }

        if (!guardFallback()) {
            return null;
        }

        return accountMapper.selectById(accountId);
    }

    /**
     * 获取分组信息（供调度器使用）
     */
    public Group getGroupById(Long groupId) {
        if (groupMapper == null) {
            return null;
        }
        return groupMapper.selectById(groupId);
    }

    /**
     * 更新缓存中的账号
     */
    public void updateAccountInCache(Account account) {
        if (schedulerCache == null || account == null) {
            return;
        }
        try {
            schedulerCache.setAccount(account);
        } catch (Exception e) {
            log.warn("[Scheduler] update account cache failed: id={} err={}", account.getId(), e.getMessage());
        }
    }

    /**
     * 初始重建
     */
    private void runInitialRebuild() {
        if (schedulerCache == null) {
            return;
        }

        try {
            List<SchedulerBucket> buckets;
            try {
                buckets = schedulerCache.listBuckets();
            } catch (Exception e) {
                log.warn("[Scheduler] list buckets failed: {}", e.getMessage());
                buckets = Collections.emptyList();
            }

            if (buckets.isEmpty()) {
                buckets = defaultBuckets();
            }

            rebuildBuckets(buckets, "startup");
        } catch (Exception e) {
            log.error("[Scheduler] initial rebuild failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Outbox 轮询任务（每分钟执行）
     */
    @Scheduled(fixedDelayString = "${sub2api.scheduler.outbox-poll-interval:10000}")
    public void pollOutbox() {
        if (schedulerCache == null || schedulerOutboxRepository == null) {
            return;
        }

        long currentWatermark;
        try {
            currentWatermark = schedulerCache.getOutboxWatermark();
        } catch (Exception e) {
            log.warn("[Scheduler] outbox watermark read failed: {}", e.getMessage());
            return;
        }

        List<SchedulerOutboxEvent> events;
        try {
            events = schedulerOutboxRepository.listAfter(currentWatermark, 200);
        } catch (Exception e) {
            log.warn("[Scheduler] outbox poll failed: {}", e.getMessage());
            return;
        }

        if (events == null || events.isEmpty()) {
            return;
        }

        long lastId = currentWatermark;
        for (SchedulerOutboxEvent event : events) {
            try {
                handleOutboxEvent(event);
                lastId = event.getId();
            } catch (Exception e) {
                log.error("[Scheduler] outbox handle failed: id={} type={} err={}",
                        event.getId(), event.getEventType(), e.getMessage());
                return;
            }
        }

        // 更新水位
        try {
            schedulerCache.setOutboxWatermark(lastId);
        } catch (Exception e) {
            log.warn("[Scheduler] outbox watermark write failed: {}", e.getMessage());
        }

        // 检查延迟
        if (!events.isEmpty()) {
            checkOutboxLag(events.get(0), lastId);
        }
    }

    /**
     * 完全重建任务（每小时执行）
     */
    @Scheduled(fixedDelayString = "${sub2api.scheduler.full-rebuild-interval:3600000}")
    public void triggerFullRebuild() {
        if (schedulerCache == null) {
            return;
        }

        try {
            List<SchedulerBucket> buckets = schedulerCache.listBuckets();
            if (buckets.isEmpty()) {
                buckets = defaultBuckets();
            }
            rebuildBuckets(buckets, "interval");
        } catch (Exception e) {
            log.error("[Scheduler] full rebuild failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理单个 outbox 事件
     */
    private void handleOutboxEvent(SchedulerOutboxEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }

        switch (event.getEventType()) {
            case SchedulerOutboxEvent.EVENT_ACCOUNT_LAST_USED:
                handleLastUsedEvent(event.getPayload());
                break;
            case SchedulerOutboxEvent.EVENT_ACCOUNT_BULK_CHANGED:
                handleBulkAccountEvent(event.getPayload());
                break;
            case SchedulerOutboxEvent.EVENT_ACCOUNT_GROUPS_CHANGED:
                handleAccountEvent(event.getAccountId(), event.getPayload());
                break;
            case SchedulerOutboxEvent.EVENT_ACCOUNT_CHANGED:
                handleAccountEvent(event.getAccountId(), event.getPayload());
                break;
            case SchedulerOutboxEvent.EVENT_GROUP_CHANGED:
                handleGroupEvent(event.getGroupId());
                break;
            case SchedulerOutboxEvent.EVENT_FULL_REBUILD:
                triggerFullRebuild();
                break;
            default:
                // 未知事件类型，忽略
                break;
        }
    }

    /**
     * 处理最后使用时间更新事件
     */
    private void handleLastUsedEvent(Map<String, Object> payload) {
        if (schedulerCache == null || payload == null) {
            return;
        }

        Object lastUsedObj = payload.get("last_used");
        if (!(lastUsedObj instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lastUsedMap = (Map<String, Object>) lastUsedObj;
        if (lastUsedMap.isEmpty()) {
            return;
        }

        Map<Long, LocalDateTime> updates = new HashMap<>();
        for (Map.Entry<String, Object> entry : lastUsedMap.entrySet()) {
            try {
                Long id = Long.parseLong(entry.getKey());
                long seconds;
                if (entry.getValue() instanceof Number) {
                    seconds = ((Number) entry.getValue()).longValue();
                } else {
                    continue;
                }
                if (seconds > 0) {
                    updates.put(id, LocalDateTime.ofEpochSecond(seconds, 0, java.time.ZoneOffset.UTC));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (!updates.isEmpty()) {
            schedulerCache.updateLastUsed(updates);
        }
    }

    /**
     * 处理批量账号变更事件
     */
    private void handleBulkAccountEvent(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }

        Object accountIdsObj = payload.get("account_ids");
        if (!(accountIdsObj instanceof List)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) accountIdsObj;
        if (rawIds.isEmpty()) {
            return;
        }

        List<Long> ids = rawIds.stream()
                .filter(id -> id != null && id.longValue() > 0)
                .map(Number::longValue)
                .distinct()
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return;
        }

        // 加载账号并更新缓存
        for (Long id : ids) {
            Account account = accountMapper.selectById(id);
            if (account != null && schedulerCache != null) {
                schedulerCache.setAccount(account);
            } else if (account == null && schedulerCache != null) {
                // 账号不存在，删除缓存
                schedulerCache.deleteAccount(id);
            }
        }

        // 重建相关分组
        Object groupIdsObj = payload.get("group_ids");
        if (groupIdsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> groupIdNums = (List<Number>) groupIdsObj;
            List<Long> groupIds = groupIdNums.stream()
                    .filter(g -> g != null && g.longValue() > 0)
                    .map(Number::longValue)
                    .collect(Collectors.toList());
            if (!groupIds.isEmpty()) {
                rebuildByGroupIds(groupIds, "account_bulk_change");
            }
        }
    }

    /**
     * 处理单个账号变更事件
     */
    private void handleAccountEvent(Long accountId, Map<String, Object> payload) {
        if (accountId == null || accountId <= 0) {
            return;
        }

        Account account = accountMapper.selectById(accountId);
        if (account != null) {
            if (schedulerCache != null) {
                schedulerCache.setAccount(account);
            }

            // 获取分组 ID
            List<Long> groupIds = extractGroupIds(payload, account);

            if (!groupIds.isEmpty()) {
                rebuildByAccount(account, groupIds, "account_change");
            } else {
                // 从数据库查询账号的分组 ID
                List<Long> accountGroupIds = accountGroupMapper.selectGroupIdsByAccountId(account.getId());
                if (!accountGroupIds.isEmpty()) {
                    rebuildByAccount(account, accountGroupIds, "account_change");
                }
            }
        } else {
            // 账号不存在，删除缓存
            if (schedulerCache != null) {
                schedulerCache.deleteAccount(accountId);
            }

            List<Long> groupIds = extractGroupIds(payload, null);
            if (!groupIds.isEmpty()) {
                rebuildByGroupIds(groupIds, "account_miss");
            }
        }
    }

    /**
     * 处理分组变更事件
     */
    private void handleGroupEvent(Long groupId) {
        if (groupId == null || groupId <= 0) {
            return;
        }
        rebuildByGroupIds(List.of(groupId), "group_change");
    }

    /**
     * 检查 outbox 延迟
     */
    private void checkOutboxLag(SchedulerOutboxEvent oldest, long watermark) {
        if (oldest == null || oldest.getCreatedAt() == null) {
            return;
        }

        Duration lag = Duration.between(oldest.getCreatedAt(), LocalDateTime.now());

        // 警告延迟
        if (outboxLagWarnSeconds > 0 && lag.getSeconds() >= outboxLagWarnSeconds) {
            log.warn("[Scheduler] outbox lag warning: {}s", lag.getSeconds());
        }

        // 触发延迟重建
        if (outboxLagRebuildSeconds > 0 && lag.getSeconds() >= outboxLagRebuildSeconds) {
            int failures = fallbackFailures.incrementAndGet();
            if (failures >= outboxLagRebuildFailures) {
                fallbackFailures.set(0);
                log.warn("[Scheduler] outbox lag rebuild triggered: lag={} failures={}", lag, failures);
                triggerFullRebuild();
            }
        }

        // 检查积压
        if (outboxBacklogRebuildRows > 0 && schedulerOutboxRepository != null) {
            try {
                long maxId = schedulerOutboxRepository.maxId();
                if (maxId - watermark >= outboxBacklogRebuildRows) {
                    log.info("[Scheduler] outbox backlog rebuild triggered: backlog={}", maxId - watermark);
                    triggerFullRebuild();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 重建分桶
     */
    private void rebuildBuckets(List<SchedulerBucket> buckets, String reason) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }

        for (SchedulerBucket bucket : buckets) {
            try {
                rebuildBucket(bucket, reason);
            } catch (Exception e) {
                log.error("[Scheduler] rebuild failed: bucket={} reason={} err={}",
                        bucket, reason, e.getMessage());
            }
        }
    }

    /**
     * 重建单个分桶
     */
    private void rebuildBucket(SchedulerBucket bucket, String reason) {
        if (schedulerCache == null) {
            return;
        }

        // 尝试获取锁
        if (!schedulerCache.tryLockBucket(bucket, LOCK_TTL.getSeconds())) {
            return;
        }

        // 加载数据
        List<Account> accounts = loadAccountsFromDB(bucket, SchedulerMode.MIXED.equals(bucket.getMode()));

        // 写入缓存
        schedulerCache.setSnapshot(bucket, accounts);

        log.debug("[Scheduler] rebuild ok: bucket={} reason={} size={}", bucket, reason, accounts.size());
    }

    /**
     * 按分组 ID 重建
     */
    private void rebuildByGroupIds(List<Long> groupIds, String reason) {
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }

        List<String> platforms = List.of(PLATFORM_ANTHROPIC, PLATFORM_GEMINI, PLATFORM_OPENAI, PLATFORM_ANTIGRAVITY);
        for (String platform : platforms) {
            for (Long groupId : groupIds) {
                rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.SINGLE), reason);
                rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.FORCED), reason);
                if (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) {
                    rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.MIXED), reason);
                }
            }
        }
    }

    /**
     * 按账号重建
     */
    private void rebuildByAccount(Account account, List<Long> groupIds, String reason) {
        if (account == null || groupIds == null || groupIds.isEmpty()) {
            return;
        }

        // 重建账号所属平台的 bucket
        rebuildBucketsForPlatform(account.getPlatform(), groupIds, reason);

        // 如果是 antigravity 平台且启用了混合调度，还需要重建 anthropic 和 gemini 的混合 bucket
        if (PLATFORM_ANTIGRAVITY.equals(account.getPlatform()) && isMixedSchedulingEnabled(account)) {
            rebuildBucketsForPlatform(PLATFORM_ANTHROPIC, groupIds, reason);
            rebuildBucketsForPlatform(PLATFORM_GEMINI, groupIds, reason);
        }
    }

    /**
     * 按平台重建分组
     */
    private void rebuildBucketsForPlatform(String platform, List<Long> groupIds, String reason) {
        if (platform == null || platform.isEmpty() || groupIds == null) {
            return;
        }

        for (Long groupId : groupIds) {
            rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.SINGLE), reason);
            rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.FORCED), reason);
            if (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) {
                rebuildBucket(new SchedulerBucket(groupId, platform, SchedulerMode.MIXED), reason);
            }
        }
    }

    /**
     * 从数据库加载账号
     */
    private List<Account> loadAccountsFromDB(SchedulerBucket bucket, boolean useMixed) {
        Long groupId = bucket.getGroupId();
        String platform = bucket.getPlatform();

        // 简单模式时使用 groupId=0
        if (isRunModeSimple()) {
            groupId = 0L;
        }

        if (useMixed) {
            // 混合模式：查询多个平台
            List<String> platforms = List.of(platform, PLATFORM_ANTIGRAVITY);
            List<Account> accounts;
            if (groupId != null && groupId > 0) {
                accounts = listSchedulableByGroupIdAndPlatforms(groupId, platforms);
            } else if (isRunModeSimple()) {
                accounts = listSchedulableByPlatforms(platforms);
            } else {
                accounts = listSchedulableUngroupedByPlatforms(platforms);
            }

            // 过滤掉未启用混合调度的 antigravity 账号
            if (accounts != null) {
                return accounts.stream()
                        .filter(acc -> !PLATFORM_ANTIGRAVITY.equals(acc.getPlatform()) || isMixedSchedulingEnabled(acc))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        // 单平台模式
        if (groupId != null && groupId > 0) {
            return listSchedulableByGroupIdAndPlatform(groupId, platform);
        }
        if (isRunModeSimple()) {
            return listSchedulableByPlatform(platform);
        }
        return listSchedulableUngroupedByPlatform(platform);
    }

    /**
     * 获取默认分桶列表
     */
    private List<SchedulerBucket> defaultBuckets() {
        List<SchedulerBucket> buckets = new ArrayList<>();
        List<String> platforms = List.of(PLATFORM_ANTHROPIC, PLATFORM_GEMINI, PLATFORM_OPENAI, PLATFORM_ANTIGRAVITY);

        for (String platform : platforms) {
            buckets.add(new SchedulerBucket(0L, platform, SchedulerMode.SINGLE));
            buckets.add(new SchedulerBucket(0L, platform, SchedulerMode.FORCED));
            if (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) {
                buckets.add(new SchedulerBucket(0L, platform, SchedulerMode.MIXED));
            }
        }

        // 非简单模式且有分组信息时，添加分组特定 bucket
        if (!isRunModeSimple() && groupMapper != null) {
            try {
                List<Group> groups = groupMapper.selectList(
                        new LambdaQueryWrapper<Group>()
                                .eq(Group::getStatus, "active")
                                .isNotNull(Group::getPlatform)
                                .ne(Group::getPlatform, "")
                );

                for (Group group : groups) {
                    String platform = group.getPlatform();
                    buckets.add(new SchedulerBucket(group.getId(), platform, SchedulerMode.SINGLE));
                    buckets.add(new SchedulerBucket(group.getId(), platform, SchedulerMode.FORCED));
                    if (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) {
                        buckets.add(new SchedulerBucket(group.getId(), platform, SchedulerMode.MIXED));
                    }
                }
            } catch (Exception e) {
                log.warn("[Scheduler] default buckets failed: {}", e.getMessage());
            }
        }

        return buckets;
    }

    /**
     * 解析调度模式
     */
    private String resolveMode(String platform, boolean hasForcePlatform) {
        if (hasForcePlatform) {
            return SchedulerMode.FORCED;
        }
        if (PLATFORM_ANTHROPIC.equals(platform) || PLATFORM_GEMINI.equals(platform)) {
            return SchedulerMode.MIXED;
        }
        return SchedulerMode.SINGLE;
    }

    /**
     * 创建分桶
     */
    private SchedulerBucket bucketFor(Long groupId, String platform, String mode) {
        return new SchedulerBucket(normalizeGroupId(groupId), platform, mode);
    }

    /**
     * 标准化分组 ID
     */
    private Long normalizeGroupId(Long groupId) {
        if (isRunModeSimple()) {
            return 0L;
        }
        if (groupId == null || groupId <= 0) {
            return 0L;
        }
        return groupId;
    }

    /**
     * Guard fallback
     */
    private boolean guardFallback() {
        if (!dbFallbackEnabled) {
            return false;
        }
        if (dbFallbackMaxQps <= 0) {
            return true;
        }
        // 简化实现：实际应该使用令牌桶或滑动窗口
        return true;
    }

    /**
     * 判断是否简单运行模式
     */
    private boolean isRunModeSimple() {
        // TODO: 从配置中获取
        return false;
    }

    /**
     * 检查是否启用混合调度
     */
    private boolean isMixedSchedulingEnabled(Account account) {
        if (account == null || account.getExtra() == null) {
            return false;
        }
        Object mixedScheduling = account.getExtra().get("mixed_scheduling");
        return Boolean.TRUE.equals(mixedScheduling);
    }

    // ========== 数据库查询辅助方法 ==========

    private List<Account> listSchedulableByGroupIdAndPlatform(Long groupId, String platform) {
        if (groupId == null || groupId <= 0 || platform == null) {
            return Collections.emptyList();
        }

        // 通过 account_groups 表查询
        List<Long> accountIds = accountGroupMapper.selectAccountIdsByGroupId(groupId);
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getId, accountIds)
                .eq(Account::getPlatform, platform)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .and(w -> w
                        .isNull(Account::getTempUnschedulableUntil)
                        .or()
                        .le(Account::getTempUnschedulableUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Account> listSchedulableByPlatform(String platform) {
        if (platform == null) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .eq(Account::getPlatform, platform)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Account> listSchedulableUngroupedByPlatform(String platform) {
        if (platform == null) {
            return Collections.emptyList();
        }

        // 查询没有分组的账号
        LocalDateTime now = LocalDateTime.now();
        List<Long> groupedAccountIds = accountGroupMapper.selectAllGroupedAccountIds();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .eq(Account::getPlatform, platform)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .notIn(groupedAccountIds != null && !groupedAccountIds.isEmpty(), Account::getId, groupedAccountIds)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Account> listSchedulableByGroupIdAndPlatforms(Long groupId, List<String> platforms) {
        if (groupId == null || groupId <= 0 || platforms == null || platforms.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> accountIds = accountGroupMapper.selectAccountIdsByGroupId(groupId);
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getId, accountIds)
                .in(Account::getPlatform, platforms)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Account> listSchedulableByPlatforms(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getPlatform, platforms)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Account> listSchedulableUngroupedByPlatforms(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> groupedAccountIds = accountGroupMapper.selectAllGroupedAccountIds();
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getPlatform, platforms)
                .eq(Account::getStatus, "active")
                .eq(Account::getSchedulable, true)
                .isNull(Account::getDeletedAt)
                .notIn(groupedAccountIds != null && !groupedAccountIds.isEmpty(), Account::getId, groupedAccountIds)
                .and(w -> w
                        .isNull(Account::getRateLimitResetAt)
                        .or()
                        .le(Account::getRateLimitResetAt, now)
                )
                .and(w -> w
                        .isNull(Account::getOverloadUntil)
                        .or()
                        .le(Account::getOverloadUntil, now)
                )
                .orderByAsc(Account::getPriority));
    }

    private List<Long> extractGroupIds(Map<String, Object> payload, Account account) {
        // 首先尝试从 payload 中提取
        if (payload != null) {
            Object groupIdsObj = payload.get("group_ids");
            if (groupIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Number> groupIdNums = (List<Number>) groupIdsObj;
                return groupIdNums.stream()
                        .filter(g -> g != null && g.longValue() > 0)
                        .map(Number::longValue)
                        .collect(Collectors.toList());
            }
        }

        // 如果 payload 中没有，尝试从 account 获取
        if (account != null) {
            return accountGroupMapper.selectGroupIdsByAccountId(account.getId());
        }
        return Collections.emptyList();
    }
}
