package com.sub2api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.user.model.entity.UserGroupRateMultiplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户专属分组倍率 Mapper
 *
 * @author Alibaba Java Code Guidelines
 */
@Mapper
public interface UserGroupRateMultiplierMapper extends BaseMapper<UserGroupRateMultiplier> {

    /**
     * 根据用户ID查询所有分组倍率
     * @return Map<groupId, rateMultiplier>
     */
    @Select("SELECT group_id, rate_multiplier FROM user_group_rate_multipliers WHERE user_id = #{userId}")
    List<Map<String, Object>> selectRatesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和分组ID查询倍率
     */
    @Select("SELECT rate_multiplier FROM user_group_rate_multipliers WHERE user_id = #{userId} AND group_id = #{groupId}")
    BigDecimal selectRateByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /**
     * 根据用户ID删除所有分组倍率
     */
    @Select("DELETE FROM user_group_rate_multipliers WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和分组ID删除
     */
    @Select("DELETE FROM user_group_rate_multipliers WHERE user_id = #{userId} AND group_id = #{groupId}")
    void deleteByUserIdAndGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
