package com.sub2api.module.account.service.scheduler;

import com.sub2api.module.account.model.entity.Account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调度缓存接口
 * 负责调度快照与账号快照的缓存读写
 *
 * @author Sub2API
 */
public interface SchedulerCache {

    /**
     * 读取快照并返回命中与否（ready + active + 数据完整）
     *
     * @param bucket 分桶
     * @return 账号列表和是否命中
     */
    GetSnapshotResult getSnapshot(SchedulerBucket bucket);

    /**
     * 写入快照并切换激活版本
     *
     * @param bucket   分桶
     * @param accounts 账号列表
     */
    void setSnapshot(SchedulerBucket bucket, List<Account> accounts);

    /**
     * 获取单账号快照
     *
     * @param accountId 账号ID
     * @return 账号对象，不存在返回 null
     */
    Account getAccount(Long accountId);

    /**
     * 写入单账号快照（包含不可调度状态）
     *
     * @param account 账号
     */
    void setAccount(Account account);

    /**
     * 删除单账号快照
     *
     * @param accountId 账号ID
     */
    void deleteAccount(Long accountId);

    /**
     * 批量更新账号的最后使用时间
     *
     * @param updates 账号ID到最后使用时间的映射
     */
    void updateLastUsed(Map<Long, LocalDateTime> updates);

    /**
     * 尝试获取分桶重建锁
     *
     * @param bucket 分桶
     * @param ttl    锁的TTL
     * @return 是否获取成功
     */
    boolean tryLockBucket(SchedulerBucket bucket, long ttlSeconds);

    /**
     * 返回已注册的分桶集合
     *
     * @return 分桶列表
     */
    List<SchedulerBucket> listBuckets();

    /**
     * 读取 outbox 水位
     *
     * @return 水位ID
     */
    long getOutboxWatermark();

    /**
     * 保存 outbox 水位
     *
     * @param id 水位ID
     */
    void setOutboxWatermark(long id);

    /**
     * 快照结果
     */
    record GetSnapshotResult(List<Account> accounts, boolean hit) {
    }
}
