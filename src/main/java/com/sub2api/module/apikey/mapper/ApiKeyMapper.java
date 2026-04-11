package com.sub2api.module.apikey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Key Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
}
