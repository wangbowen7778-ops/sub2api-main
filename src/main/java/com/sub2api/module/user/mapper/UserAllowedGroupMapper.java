package com.sub2api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.user.model.entity.UserAllowedGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户允许访问的分组关联 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface UserAllowedGroupMapper extends BaseMapper<UserAllowedGroup> {
}
