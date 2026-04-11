package com.sub2api.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.admin.model.entity.ScheduledTestPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时测试计划 Mapper
 */
@Mapper
public interface ScheduledTestPlanMapper extends BaseMapper<ScheduledTestPlan> {

    /**
     * 根据账号ID查询计划
     */
    List<ScheduledTestPlan> selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 查询待执行计划
     */
    List<ScheduledTestPlan> selectPendingPlans();
}
