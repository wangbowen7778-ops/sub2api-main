package com.sub2api.module.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.account.service.scheduler.SchedulerOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 调度 Outbox Mapper
 *
 * @author Sub2API
 */
@Mapper
public interface SchedulerOutboxMapper extends BaseMapper<SchedulerOutboxEvent> {

    /**
     * 查询指定 ID 之后的事件
     *
     * @param afterId 起始 ID
     * @param limit   最大返回数量
     * @return 事件列表
     */
    @Select("SELECT id, event_type, account_id, group_id, payload, created_at " +
            "FROM scheduler_outbox " +
            "WHERE id > #{afterId} " +
            "ORDER BY id ASC " +
            "LIMIT #{limit}")
    List<SchedulerOutboxEvent> selectAfter(Long afterId, int limit);

    /**
     * 获取最大 ID
     *
     * @return 最大 ID
     */
    @Select("SELECT COALESCE(MAX(id), 0) FROM scheduler_outbox")
    long selectMaxId();
}
