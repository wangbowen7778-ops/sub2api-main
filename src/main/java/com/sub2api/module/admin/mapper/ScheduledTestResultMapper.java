package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.ScheduledTestResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时测试结果 Mapper
 */
@Mapper
public interface ScheduledTestResultMapper extends BaseMapper<ScheduledTestResult> {

    /**
     * 根据计划ID查询结果
     */
    List<ScheduledTestResult> selectByPlanId(@Param("planId") Long planId, @Param("limit") int limit);

    /**
     * 删除计划的所有结果
     */
    void deleteByPlanId(@Param("planId") Long planId);
}
