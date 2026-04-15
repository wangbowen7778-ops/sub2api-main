package com.sub2api.module.account.service.scheduler;

import com.sub2api.module.account.mapper.SchedulerOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 调度 Outbox 仓储实现
 *
 * @author Sub2API
 */
@Repository
@RequiredArgsConstructor
public class SchedulerOutboxRepositoryImpl implements SchedulerOutboxRepository {

    private final SchedulerOutboxMapper schedulerOutboxMapper;

    @Override
    public List<SchedulerOutboxEvent> listAfter(Long afterId, int limit) {
        if (afterId == null) {
            afterId = 0L;
        }
        if (limit <= 0) {
            limit = 200;
        }
        return schedulerOutboxMapper.selectAfter(afterId, limit);
    }

    @Override
    public long maxId() {
        return schedulerOutboxMapper.selectMaxId();
    }
}
