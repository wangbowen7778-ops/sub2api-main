package com.sub2api.module.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.channel.model.entity.ChannelModelPricing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠道模型定价 Mapper
 */
@Mapper
public interface ChannelModelPricingMapper extends BaseMapper<ChannelModelPricing> {

    /**
     * 根据渠道ID查询定价列表
     */
    @Select("SELECT * FROM channel_model_pricing WHERE channel_id = #{channelId}")
    List<ChannelModelPricing> selectByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据多个渠道ID批量查询定价列表
     */
    @Select("SELECT * FROM channel_model_pricing WHERE channel_id = ANY(#{channelIds})")
    List<ChannelModelPricing> selectByChannelIds(@Param("channelIds") List<Long> channelIds);
}
