package com.sub2api.module.dashboard.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * Dashboard Daily Active Users Entity
 * 按天聚合的活跃用户（去重）
 */
@Data
@TableName("usage_dashboard_daily_users")
public class UsageDashboardDailyUsers {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 桶日期
     */
    private LocalDate bucketDate;

    /**
     * 用户 ID
     */
    private Long userId;
}
