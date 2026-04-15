package com.sub2api.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.billing.model.entity.UsageCleanupTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用记录清理任务 Mapper
 *
 * @author Sub2API
 */
@Mapper
public interface UsageCleanupTaskMapper extends BaseMapper<UsageCleanupTask> {
}
