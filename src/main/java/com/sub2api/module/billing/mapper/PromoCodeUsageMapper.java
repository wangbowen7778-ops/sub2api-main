package com.sub2api.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.billing.model.entity.PromoCodeUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠码使用记录 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface PromoCodeUsageMapper extends BaseMapper<PromoCodeUsage> {
}
