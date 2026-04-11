package com.sub2api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.user.model.entity.UserSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户订阅 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {
}
