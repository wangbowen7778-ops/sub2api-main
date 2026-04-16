package com.sub2api.module.account.service.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的调度缓存
 * 基于 Go 版本的 scheduler_cache.go
 *
 * @author Sub2API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSchedulerCache implements SchedulerCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 键前缀
    private static final String SCHEDULER_BUCKET_SET_KEY = "sched:buckets";
    private static final String SCHEDULER_OUTBOX_WATERMARK_KEY = "sched:outbox:watermark";
    private static final String SCHEDULER_ACCOUNT_PREFIX = "sched:acc:";
    private static final String SCHEDULER_ACCOUNT_META_PREFIX = "sched:meta:";
    private static final String SCHEDULER_ACTIVE_PREFIX = "sched:active:";
    private static final String SCHEDULER_READY_PREFIX = "sched:ready:";
    private static final String SCHEDULER_VERSION_PREFIX = "sched:ver:";
    private static final String SCHEDULER_SNAPSHOT_PREFIX = "sched:";
    private static final String SCHEDULER_LOCK_PREFIX = "sched:lock:";

    private static final int MGET_CHUNK_SIZE = 128;
    private static final int WRITE_CHUNK_SIZE = 256;

    @Override
    public GetSnapshotResult getSnapshot(SchedulerBucket bucket) {
        String readyKey = schedulerBucketKey(SCHEDULER_READY_PREFIX, bucket);
        String readyVal = redisTemplate.opsForValue().get(readyKey);

        if (readyVal == null) {
            return new GetSnapshotResult(null, false);
        }
        if (!"1".equals(readyVal)) {
            return new GetSnapshotResult(null, false);
        }

        String activeKey = schedulerBucketKey(SCHEDULER_ACTIVE_PREFIX, bucket);
        String activeVal = redisTemplate.opsForValue().get(activeKey);
        if (activeVal == null) {
            return new GetSnapshotResult(null, false);
        }

        String snapshotKey = schedulerSnapshotKey(bucket, activeVal);
        Set<String> ids = redisTemplate.opsForZSet().range(snapshotKey, 0, -1);
        if (ids == null || ids.isEmpty()) {
            // 空快照视为缓存未命中
            return new GetSnapshotResult(null, false);
        }

        // 分块获取账号数据
        List<String> idList = new ArrayList<>(ids);
        List<Account> accounts = new ArrayList<>();

        for (int i = 0; i < idList.size(); i += MGET_CHUNK_SIZE) {
            int end = Math.min(i + MGET_CHUNK_SIZE, idList.size());
            List<String> chunk = idList.subList(i, end);

            List<String> keys = chunk.stream()
                    .map(id -> SCHEDULER_ACCOUNT_META_PREFIX + id)
                    .toList();

            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return new GetSnapshotResult(null, false);
            }

            for (String val : values) {
                if (val == null) {
                    return new GetSnapshotResult(null, false);
                }
                try {
                    Account account = objectMapper.readValue(val, Account.class);
                    accounts.add(account);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize account", e);
                    return new GetSnapshotResult(null, false);
                }
            }
        }

        return new GetSnapshotResult(accounts, true);
    }

    @Override
    public void setSnapshot(SchedulerBucket bucket, List<Account> accounts) {
        String activeKey = schedulerBucketKey(SCHEDULER_ACTIVE_PREFIX, bucket);
        String oldActive = redisTemplate.opsForValue().get(activeKey);

        // 增加版本号
        String versionKey = schedulerBucketKey(SCHEDULER_VERSION_PREFIX, bucket);
        Long version = redisTemplate.opsForValue().increment(versionKey);
        String versionStr = String.valueOf(version);

        String snapshotKey = schedulerSnapshotKey(bucket, versionStr);

        // 写入账号数据
        writeAccounts(accounts);

        // 使用管道执行批量操作
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        if (accounts != null && !accounts.isEmpty()) {
            // 使用序号作为 score，保持数据库返回的排序语义
            List<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> members = new ArrayList<>();
            for (int idx = 0; idx < accounts.size(); idx++) {
                final int index = idx;
                members.add(org.springframework.data.redis.core.ZSetOperations.TypedTuple.of(
                        String.valueOf(accounts.get(index).getId()),
                        (double) index
                ));
            }

            // 分块写入
            for (int start = 0; start < members.size(); start += WRITE_CHUNK_SIZE) {
                int end = Math.min(start + WRITE_CHUNK_SIZE, members.size());
                Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> chunk = new java.util.HashSet<>(members.subList(start, end));
                zSetOps.add(snapshotKey, chunk);
            }
        } else {
            redisTemplate.delete(snapshotKey);
        }

        // 设置 active 和 ready 标记
        redisTemplate.opsForValue().set(activeKey, versionStr);
        redisTemplate.opsForValue().set(schedulerBucketKey(SCHEDULER_READY_PREFIX, bucket), "1");
        redisTemplate.opsForSet().add(SCHEDULER_BUCKET_SET_KEY, bucket.toString());

        // 删除旧版本快照
        if (oldActive != null && !oldActive.equals(versionStr)) {
            String oldSnapshotKey = schedulerSnapshotKey(bucket, oldActive);
            redisTemplate.delete(oldSnapshotKey);
        }
    }

    @Override
    public Account getAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            return null;
        }
        String key = SCHEDULER_ACCOUNT_PREFIX + accountId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return null;
        }
        try {
            return objectMapper.readValue(val, Account.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize account: {}", accountId, e);
            return null;
        }
    }

    @Override
    public void setAccount(Account account) {
        if (account == null || account.getId() == null || account.getId() <= 0) {
            return;
        }
        writeAccounts(List.of(account));
    }

    @Override
    public void deleteAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            return;
        }
        String idStr = String.valueOf(accountId);
        redisTemplate.delete(Arrays.asList(
                SCHEDULER_ACCOUNT_PREFIX + idStr,
                SCHEDULER_ACCOUNT_META_PREFIX + idStr
        ));
    }

    @Override
    public void updateLastUsed(Map<Long, LocalDateTime> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Long id : updates.keySet()) {
            keys.add(SCHEDULER_ACCOUNT_PREFIX + id);
            ids.add(id);
        }

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return;
        }

        Map<String, String> toUpdate = new HashMap<>();
        Map<String, String> metaToUpdate = new HashMap<>();

        for (int i = 0; i < values.size(); i++) {
            String val = values.get(i);
            if (val == null) {
                continue;
            }
            try {
                Account account = objectMapper.readValue(val, Account.class);
                account.setLastUsedAt(updates.get(ids.get(i)));

                String updated = objectMapper.writeValueAsString(account);
                toUpdate.put(keys.get(i), updated);

                String meta = objectMapper.writeValueAsString(buildSchedulerMetadataAccount(account));
                metaToUpdate.put(SCHEDULER_ACCOUNT_META_PREFIX + ids.get(i), meta);
            } catch (JsonProcessingException e) {
                log.error("Failed to update account lastUsed", e);
            }
        }

        // 批量更新
        if (!toUpdate.isEmpty()) {
            redisTemplate.opsForValue().multiSet(toUpdate);
        }
        if (!metaToUpdate.isEmpty()) {
            redisTemplate.opsForValue().multiSet(metaToUpdate);
        }
    }

    @Override
    public boolean tryLockBucket(SchedulerBucket bucket, long ttlSeconds) {
        String key = schedulerBucketKey(SCHEDULER_LOCK_PREFIX, bucket);
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), ttlSeconds, TimeUnit.SECONDS));
    }

    @Override
    public List<SchedulerBucket> listBuckets() {
        Set<String> raw = redisTemplate.opsForSet().members(SCHEDULER_BUCKET_SET_KEY);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<SchedulerBucket> buckets = new ArrayList<>();
        for (String entry : raw) {
            SchedulerBucket bucket = SchedulerBucket.parse(entry);
            if (bucket != null) {
                buckets.add(bucket);
            }
        }
        return buckets;
    }

    @Override
    public long getOutboxWatermark() {
        String val = redisTemplate.opsForValue().get(SCHEDULER_OUTBOX_WATERMARK_KEY);
        if (val == null || val.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void setOutboxWatermark(long id) {
        redisTemplate.opsForValue().set(SCHEDULER_OUTBOX_WATERMARK_KEY, String.valueOf(id));
    }

    /**
     * 生成带前缀的分桶键
     */
    private String schedulerBucketKey(String prefix, SchedulerBucket bucket) {
        return prefix + bucket.getGroupId() + ":" + bucket.getPlatform() + ":" + bucket.getMode();
    }

    /**
     * 生成快照键
     */
    private String schedulerSnapshotKey(SchedulerBucket bucket, String version) {
        return SCHEDULER_SNAPSHOT_PREFIX + bucket.getGroupId() + ":" + bucket.getPlatform() + ":" + bucket.getMode() + ":v" + version;
    }

    /**
     * 批量写入账号数据
     */
    private void writeAccounts(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        Map<String, String> toUpdate = new HashMap<>();
        Map<String, String> metaToUpdate = new HashMap<>();

        for (Account account : accounts) {
            if (account == null || account.getId() == null) {
                continue;
            }
            try {
                String idStr = String.valueOf(account.getId());
                String fullPayload = objectMapper.writeValueAsString(account);
                String metaPayload = objectMapper.writeValueAsString(buildSchedulerMetadataAccount(account));

                toUpdate.put(SCHEDULER_ACCOUNT_PREFIX + idStr, fullPayload);
                metaToUpdate.put(SCHEDULER_ACCOUNT_META_PREFIX + idStr, metaPayload);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize account: {}", account.getId(), e);
            }
        }

        if (!toUpdate.isEmpty()) {
            redisTemplate.opsForValue().multiSet(toUpdate);
        }
        if (!metaToUpdate.isEmpty()) {
            redisTemplate.opsForValue().multiSet(metaToUpdate);
        }
    }

    /**
     * 构建调度器元数据账号（只包含调度相关的字段）
     */
    private Account buildSchedulerMetadataAccount(Account account) {
        Account meta = new Account();
        meta.setId(account.getId());
        meta.setName(account.getName());
        meta.setPlatform(account.getPlatform());
        meta.setType(account.getType());
        meta.setConcurrency(account.getConcurrency());
        meta.setLoadFactor(account.getLoadFactor());
        meta.setPriority(account.getPriority());
        meta.setRateMultiplier(account.getRateMultiplier());
        meta.setStatus(account.getStatus());
        meta.setLastUsedAt(account.getLastUsedAt());
        meta.setExpiresAt(account.getExpiresAt());
        meta.setAutoPauseOnExpired(account.getAutoPauseOnExpired());
        meta.setSchedulable(account.getSchedulable());
        meta.setRateLimitedAt(account.getRateLimitedAt());
        meta.setRateLimitResetAt(account.getRateLimitResetAt());
        meta.setOverloadUntil(account.getOverloadUntil());
        meta.setTempUnschedulableUntil(account.getTempUnschedulableUntil());
        meta.setTempUnschedulableReason(account.getTempUnschedulableReason());
        meta.setSessionWindowStart(account.getSessionWindowStart());
        meta.setSessionWindowEnd(account.getSessionWindowEnd());
        meta.setSessionWindowStatus(account.getSessionWindowStatus());

        // 过滤凭证字段
        if (account.getCredentials() != null) {
            Map<String, Object> filtered = new HashMap<>();
            for (String key : new String[]{"model_mapping", "api_key", "project_id", "oauth_type"}) {
                Object value = account.getCredentials().get(key);
                if (value != null) {
                    filtered.put(key, value);
                }
            }
            if (!filtered.isEmpty()) {
                meta.setCredentials(filtered);
            }
        }

        // 过滤 Extra 字段
        if (account.getExtra() != null) {
            Map<String, Object> filtered = new HashMap<>();
            for (String key : new String[]{"mixed_scheduling", "window_cost_limit", "window_cost_sticky_reserve", "max_sessions", "session_idle_timeout_minutes"}) {
                Object value = account.getExtra().get(key);
                if (value != null) {
                    filtered.put(key, value);
                }
            }
            if (!filtered.isEmpty()) {
                meta.setExtra(filtered);
            }
        }

        return meta;
    }
}
