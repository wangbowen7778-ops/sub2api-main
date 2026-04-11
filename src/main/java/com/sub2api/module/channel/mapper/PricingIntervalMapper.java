package com.sub2api.module.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.channel.model.entity.PricingInterval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 定价区间 Mapper
 */
@Mapper
public interface PricingIntervalMapper extends BaseMapper<PricingInterval> {

    /**
     * 根据定价ID查询区间列表
     */
    @Select("SELECT * FROM pricing_intervals WHERE pricing_id = #{pricingId} ORDER BY sort_order")
    List<PricingInterval> selectByPricingId(@Param("pricingId") Long pricingId);

    /**
     * 根据多个定价ID批量查询区间列表
     */
    @Select("SELECT * FROM pricing_intervals WHERE pricing_id = ANY(#{pricingIds}) ORDER BY sort_order")
    List<PricingInterval> selectByPricingIds(@Param("pricingIds") List<Long> pricingIds);
}
