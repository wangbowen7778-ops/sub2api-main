package com.sub2api.module.dashboard.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dashboard Hourly Active Users Entity
 * 按小时聚合的活跃用户（去重）
 */
@Data
@TableName("usage_dashboard_hourly_users")
public class UsageDashboardHourlyUsers {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 桶开始时间（整点）
     */
    private LocalDateTime bucketStart;

    /**
     * 用户 ID
     */
    private Long userId;
}
