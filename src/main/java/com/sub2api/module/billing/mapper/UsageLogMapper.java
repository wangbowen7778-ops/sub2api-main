package com.sub2api.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.billing.model.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用量日志 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {
}
