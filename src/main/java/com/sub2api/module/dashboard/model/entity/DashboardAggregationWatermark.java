package com.sub2api.module.dashboard.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dashboard Aggregation Watermark Entity
 * 仪表盘聚合水位标记（单行表）
 */
@Data
@TableName("usage_dashboard_aggregation_watermark")
public class DashboardAggregationWatermark {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 最后聚合时间
     */
    private LocalDateTime lastAggregatedAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
