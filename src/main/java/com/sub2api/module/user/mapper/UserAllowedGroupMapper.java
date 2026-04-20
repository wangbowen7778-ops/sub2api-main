package com.sub2api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.user.model.entity.UserAllowedGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户允许访问的分组关联 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface UserAllowedGroupMapper extends BaseMapper<UserAllowedGroup> {

    /**
     * 根据用户ID查询允许的分组ID列表
     */
    @Select("SELECT group_id FROM user_allowed_groups WHERE user_id = #{userId}")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID删除所有允许分组关联
     */
    @Select("DELETE FROM user_allowed_groups WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
