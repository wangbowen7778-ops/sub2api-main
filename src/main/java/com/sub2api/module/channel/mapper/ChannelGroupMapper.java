package com.sub2api.module.channel.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 渠道分组关联 Mapper
 * 表名: channel_groups
 */
@Mapper
public interface ChannelGroupMapper {

    /**
     * 删除渠道的所有分组关联
     */
    void deleteByChannelId(@Param("channelId") Long channelId);

    /**
     * 批量插入渠道分组关联
     */
    void insertBatch(@Param("channelId") Long channelId, @Param("groupIds") List<Long> groupIds);

    /**
     * 替换渠道的分组关联（先删后插）
     */
    default void replaceGroupIds(@Param("channelId") Long channelId, @Param("groupIds") List<Long> groupIds) {
        deleteByChannelId(channelId);
        if (groupIds != null && !groupIds.isEmpty()) {
            insertBatch(channelId, groupIds);
        }
    }
}
