package com.sub2api.module.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.dashboard.model.entity.*;
import org.apache.ibatis.annotations.*;

/**
 * Dashboard Aggregation Mapper
 * 仪表盘预聚合 Mapper
 */
@Mapper
public interface DashboardAggregationMapper extends BaseMapper<DashboardAggregationWatermark> {

    /**
     * 获取聚合水位
     */
    @Select("SELECT last_aggregated_at FROM usage_dashboard_aggregation_watermark WHERE id = 1")
    java.time.LocalDateTime getAggregationWatermark();

    /**
     * 更新聚合水位
     */
    @Insert("""
        INSERT INTO usage_dashboard_aggregation_watermark (id, last_aggregated_at, updated_at)
        VALUES (1, #{aggregatedAt}, NOW())
        ON CONFLICT (id) DO UPDATE SET last_aggregated_at = EXCLUDED.last_aggregated_at, updated_at = EXCLUDED.updated_at
        """)
    void updateAggregationWatermark(@Param("aggregatedAt") java.time.LocalDateTime aggregatedAt);

    /**
     * 插入小时活跃用户
     */
    @Insert("""
        INSERT INTO usage_dashboard_hourly_users (bucket_start, user_id)
        SELECT DISTINCT
            date_trunc('hour', created_at AT TIME ZONE #{timezone}) AT TIME ZONE #{timezone} AS bucket_start,
            user_id
        FROM usage_logs
        WHERE created_at >= #{startTime} AND created_at < #{endTime}
        ON CONFLICT DO NOTHING
        """)
    int insertHourlyActiveUsers(@Param("startTime") java.time.LocalDateTime startTime,
                                 @Param("endTime") java.time.LocalDateTime endTime,
                                 @Param("timezone") String timezone);

    /**
     * 插入天活跃用户
     */
    @Insert("""
        INSERT INTO usage_dashboard_daily_users (bucket_date, user_id)
        SELECT DISTINCT
            (bucket_start AT TIME ZONE #{timezone})::date AS bucket_date,
            user_id
        FROM usage_dashboard_hourly_users
        WHERE bucket_start >= #{startTime} AND bucket_start < #{endTime}
        ON CONFLICT DO NOTHING
        """)
    int insertDailyActiveUsers(@Param("startTime") java.time.LocalDateTime startTime,
                                @Param("endTime") java.time.LocalDateTime endTime,
                                @Param("timezone") String timezone);

    /**
     * Upsert 小时聚合
     */
    @Insert("""
        WITH hourly AS (
            SELECT
                date_trunc('hour', created_at AT TIME ZONE #{timezone}) AT TIME ZONE #{timezone} AS bucket_start,
                COUNT(*) AS total_requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(cache_creation_tokens), 0) AS cache_creation_tokens,
                COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                COALESCE(SUM(total_cost), 0) AS total_cost,
                COALESCE(SUM(actual_cost), 0) AS actual_cost,
                COALESCE(SUM(COALESCE(duration_ms, 0)), 0) AS total_duration_ms
            FROM usage_logs
            WHERE created_at >= #{startTime} AND created_at < #{endTime}
            GROUP BY 1
        ),
        user_counts AS (
            SELECT bucket_start, COUNT(*) AS active_users
            FROM usage_dashboard_hourly_users
            WHERE bucket_start >= #{startTime} AND bucket_start < #{endTime}
            GROUP BY bucket_start
        )
        INSERT INTO usage_dashboard_hourly (
            bucket_start,
            total_requests,
            input_tokens,
            output_tokens,
            cache_creation_tokens,
            cache_read_tokens,
            total_cost,
            actual_cost,
            total_duration_ms,
            active_users,
            computed_at
        )
        SELECT
            hourly.bucket_start,
            hourly.total_requests,
            hourly.input_tokens,
            hourly.output_tokens,
            hourly.cache_creation_tokens,
            hourly.cache_read_tokens,
            hourly.total_cost,
            hourly.actual_cost,
            hourly.total_duration_ms,
            COALESCE(user_counts.active_users, 0) AS active_users,
            NOW()
        FROM hourly
        LEFT JOIN user_counts ON user_counts.bucket_start = hourly.bucket_start
        ON CONFLICT (bucket_start)
        DO UPDATE SET
            total_requests = EXCLUDED.total_requests,
            input_tokens = EXCLUDED.input_tokens,
            output_tokens = EXCLUDED.output_tokens,
            cache_creation_tokens = EXCLUDED.cache_creation_tokens,
            cache_read_tokens = EXCLUDED.cache_read_tokens,
            total_cost = EXCLUDED.total_cost,
            actual_cost = EXCLUDED.actual_cost,
            total_duration_ms = EXCLUDED.total_duration_ms,
            active_users = EXCLUDED.active_users,
            computed_at = EXCLUDED.computed_at
        """)
    int upsertHourlyAggregates(@Param("startTime") java.time.LocalDateTime startTime,
                                @Param("endTime") java.time.LocalDateTime endTime,
                                @Param("timezone") String timezone);

    /**
     * Upsert 天聚合
     */
    @Insert("""
        WITH daily AS (
            SELECT
                (bucket_start AT TIME ZONE #{timezone})::date AS bucket_date,
                COALESCE(SUM(total_requests), 0) AS total_requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(cache_creation_tokens), 0) AS cache_creation_tokens,
                COALESCE(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                COALESCE(SUM(total_cost), 0) AS total_cost,
                COALESCE(SUM(actual_cost), 0) AS actual_cost,
                COALESCE(SUM(total_duration_ms), 0) AS total_duration_ms
            FROM usage_dashboard_hourly
            WHERE bucket_start >= #{startTime} AND bucket_start < #{endTime}
            GROUP BY (bucket_start AT TIME ZONE #{timezone})::date
        ),
        user_counts AS (
            SELECT bucket_date, COUNT(*) AS active_users
            FROM usage_dashboard_daily_users
            WHERE bucket_date >= #{startDate} AND bucket_date < #{endDate}
            GROUP BY bucket_date
        )
        INSERT INTO usage_dashboard_daily (
            bucket_date,
            total_requests,
            input_tokens,
            output_tokens,
            cache_creation_tokens,
            cache_read_tokens,
            total_cost,
            actual_cost,
            total_duration_ms,
            active_users,
            computed_at
        )
        SELECT
            daily.bucket_date,
            daily.total_requests,
            daily.input_tokens,
            daily.output_tokens,
            daily.cache_creation_tokens,
            daily.cache_read_tokens,
            daily.total_cost,
            daily.actual_cost,
            daily.total_duration_ms,
            COALESCE(user_counts.active_users, 0) AS active_users,
            NOW()
        FROM daily
        LEFT JOIN user_counts ON user_counts.bucket_date = daily.bucket_date
        ON CONFLICT (bucket_date)
        DO UPDATE SET
            total_requests = EXCLUDED.total_requests,
            input_tokens = EXCLUDED.input_tokens,
            output_tokens = EXCLUDED.output_tokens,
            cache_creation_tokens = EXCLUDED.cache_creation_tokens,
            cache_read_tokens = EXCLUDED.cache_read_tokens,
            total_cost = EXCLUDED.total_cost,
            actual_cost = EXCLUDED.actual_cost,
            total_duration_ms = EXCLUDED.total_duration_ms,
            active_users = EXCLUDED.active_users,
            computed_at = EXCLUDED.computed_at
        """)
    int upsertDailyAggregates(@Param("startTime") java.time.LocalDateTime startTime,
                               @Param("endTime") java.time.LocalDateTime endTime,
                               @Param("startDate") java.time.LocalDate startDate,
                               @Param("endDate") java.time.LocalDate endDate,
                               @Param("timezone") String timezone);

    /**
     * 清理小时聚合（按截止时间）
     */
    @Delete("DELETE FROM usage_dashboard_hourly WHERE bucket_start < #{cutoff}")
    int cleanupHourlyAggregates(@Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * 清理小时活跃用户（按截止时间）
     */
    @Delete("DELETE FROM usage_dashboard_hourly_users WHERE bucket_start < #{cutoff}")
    int cleanupHourlyUsers(@Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * 清理天聚合（按截止日期）
     */
    @Delete("DELETE FROM usage_dashboard_daily WHERE bucket_date < #{cutoff}")
    int cleanupDailyAggregates(@Param("cutoff") java.time.LocalDate cutoff);

    /**
     * 清理天活跃用户（按截止日期）
     */
    @Delete("DELETE FROM usage_dashboard_daily_users WHERE bucket_date < #{cutoff}")
    int cleanupDailyUsers(@Param("cutoff") java.time.LocalDate cutoff);

    /**
     * 删除小时范围内数据（用于重新计算）
     */
    @Delete("DELETE FROM usage_dashboard_hourly WHERE bucket_start >= #{start} AND bucket_start < #{end}")
    int deleteHourlyRange(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    /**
     * 删除小时用户范围内数据（用于重新计算）
     */
    @Delete("DELETE FROM usage_dashboard_hourly_users WHERE bucket_start >= #{start} AND bucket_start < #{end}")
    int deleteHourlyUsersRange(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    /**
     * 删除天范围内数据（用于重新计算）
     */
    @Delete("DELETE FROM usage_dashboard_daily WHERE bucket_date >= #{start} AND bucket_date < #{end}")
    int deleteDailyRange(@Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);

    /**
     * 删除天用户范围内数据（用于重新计算）
     */
    @Delete("DELETE FROM usage_dashboard_daily_users WHERE bucket_date >= #{start} AND bucket_date < #{end}")
    int deleteDailyUsersRange(@Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);

    /**
     * 清理 usage_logs（分批删除）
     */
    @Select("SELECT ctid FROM usage_logs WHERE created_at < #{cutoff} LIMIT #{batchSize}")
    java.util.List<Object> selectUsageLogsCtids(@Param("cutoff") java.time.LocalDateTime cutoff, @Param("batchSize") int batchSize);

    @Delete("DELETE FROM usage_logs WHERE ctid IN (SELECT ctid FROM usage_logs WHERE created_at < #{cutoff} LIMIT #{batchSize})")
    int deleteUsageLogsBatch(@Param("cutoff") java.time.LocalDateTime cutoff, @Param("batchSize") int batchSize);

    /**
     * 检查 usage_logs 是否为分区表
     */
    @Select("""
        SELECT EXISTS(
            SELECT 1
            FROM pg_partitioned_table pt
            JOIN pg_class c ON c.oid = pt.partrelid
            WHERE c.relname = 'usage_logs'
        )
        """)
    boolean isUsageLogsPartitioned();

    /**
     * 获取 usage_logs 分区列表
     */
    @Select("""
        SELECT c.relname
        FROM pg_inherits
        JOIN pg_class c ON c.oid = pg_inherits.inhrelid
        JOIN pg_class p ON p.oid = pg_inherits.inhparent
        WHERE p.relname = 'usage_logs'
        """)
    java.util.List<String> getUsageLogsPartitions();

    /**
     * 删除指定月份的分区
     */
    @Delete("DROP TABLE IF EXISTS ${partitionName}")
    void dropPartition(@Param("partitionName") String partitionName);
}
