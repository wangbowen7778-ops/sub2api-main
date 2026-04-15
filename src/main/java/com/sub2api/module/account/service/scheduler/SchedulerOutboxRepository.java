package com.sub2api.module.account.service.scheduler;

import java.util.List;

/**
 * 调度 Outbox 仓储接口
 * 提供调度 outbox 的读取接口
 *
 * @author Sub2API
 */
public interface SchedulerOutboxRepository {

    /**
     * 读取指定 ID 之后的事件
     *
     * @param afterId 起始 ID
     * @param limit   最大返回数量
     * @return 事件列表
     */
    List<SchedulerOutboxEvent> listAfter(Long afterId, int limit);

    /**
     * 获取最大事件 ID
     *
     * @return 最大 ID
     */
    long maxId();
}
