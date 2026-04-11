package com.sub2api.module.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.channel.model.entity.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠道 Mapper
 */
@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

    /**
     * 根据名称查询是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM channels WHERE name = #{name})")
    boolean existsByName(@Param("name") String name);

    /**
     * 根据名称查询是否存在（排除指定ID）
     */
    @Select("SELECT EXISTS(SELECT 1 FROM channels WHERE name = #{name} AND id != #{excludeId})")
    boolean existsByNameExcluding(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * 查询渠道关联的分组ID列表
     */
    @Select("SELECT group_id FROM channel_groups WHERE channel_id = #{channelId} ORDER BY group_id")
    List<Long> selectGroupIdsByChannelId(@Param("channelId") Long channelId);

    /**
     * 查询分组关联的渠道ID
     */
    @Select("SELECT channel_id FROM channel_groups WHERE group_id = #{groupId}")
    Long selectChannelIdByGroupId(@Param("groupId") Long groupId);

    /**
     * 查询分组关联的渠道ID列表
     */
    @Select("SELECT channel_id FROM channel_groups WHERE group_id = ANY(#{groupIds}) AND channel_id != #{excludeChannelId}")
    List<Long> selectChannelIdsByGroupIdsExcluding(@Param("groupIds") List<Long> groupIds, @Param("excludeChannelId") Long excludeChannelId);
}
