package com.sub2api.module.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.billing.model.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dashboard 统计 Mapper
 */
@Mapper
public interface DashboardMapper extends BaseMapper<UsageLog> {

    /**
     * 获取今日用户总数
     */
    @Select("SELECT COUNT(*) FROM users WHERE created_at >= #{todayStart}")
    long countTotalUsers(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 获取今日新增用户数
     */
    @Select("SELECT COUNT(*) FROM users WHERE created_at >= #{todayStart}")
    long countTodayNewUsers(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 获取活跃用户数 (今日有请求的用户)
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM usage_logs WHERE created_at >= #{todayStart}")
    long countActiveUsers(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 获取当前小时活跃用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM usage_logs WHERE created_at >= #{hourStart}")
    long countHourlyActiveUsers(@Param("hourStart") LocalDateTime hourStart);

    /**
     * 获取 API Key 统计
     */
    @Select("SELECT COUNT(*) FROM api_keys")
    long countTotalApiKeys();

    @Select("SELECT COUNT(*) FROM api_keys WHERE status = 'active'")
    long countActiveApiKeys();

    /**
     * 获取账号统计
     */
    @Select("SELECT COUNT(*) FROM accounts")
    long countTotalAccounts();

    @Select("SELECT COUNT(*) FROM accounts WHERE status = 'active' AND schedulable = true")
    long countNormalAccounts();

    @Select("SELECT COUNT(*) FROM accounts WHERE status = 'error'")
    long countErrorAccounts();

    @Select("SELECT COUNT(*) FROM accounts WHERE rate_limit_reset_at > #{now}")
    long countRateLimitAccounts(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM accounts WHERE overload_until > #{now}")
    long countOverloadAccounts(@Param("now") LocalDateTime now);

    /**
     * 获取累计用量统计
     */
    @Select("SELECT COUNT(*) FROM usage_logs")
    long countTotalRequests();

    @Select("SELECT COALESCE(SUM(input_tokens), 0) FROM usage_logs")
    long sumTotalInputTokens();

    @Select("SELECT COALESCE(SUM(output_tokens), 0) FROM usage_logs")
    long sumTotalOutputTokens();

    /**
     * 获取今日用量统计
     */
    @Select("SELECT COUNT(*) FROM usage_logs WHERE created_at >= #{todayStart}")
    long countTodayRequests(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT COALESCE(SUM(input_tokens), 0) FROM usage_logs WHERE created_at >= #{todayStart}")
    long sumTodayInputTokens(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT COALESCE(SUM(output_tokens), 0) FROM usage_logs WHERE created_at >= #{todayStart}")
    long sumTodayOutputTokens(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 获取用量趋势 (按天)
     */
    @Select("""
        SELECT
            DATE(created_at) as date,
            COUNT(*) as requests,
            COALESCE(SUM(input_tokens), 0) as input_tokens,
            COALESCE(SUM(output_tokens), 0) as output_tokens,
            COALESCE(SUM(input_tokens + output_tokens), 0) as total_tokens
        FROM usage_logs
        WHERE created_at >= #{startTime} AND created_at < #{endTime}
        GROUP BY DATE(created_at)
        ORDER BY date
        """)
    List<UsageTrendRow> selectUsageTrendByDay(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 获取模型用量统计
     */
    @Select("""
        SELECT
            model,
            COUNT(*) as requests,
            COALESCE(SUM(input_tokens), 0) as input_tokens,
            COALESCE(SUM(output_tokens), 0) as output_tokens,
            COALESCE(SUM(input_tokens + output_tokens), 0) as total_tokens
        FROM usage_logs
        WHERE created_at >= #{startTime} AND created_at < #{endTime}
        GROUP BY model
        ORDER BY total_tokens DESC
        """)
    List<ModelUsageRow> selectModelUsageStats(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 用量趋势行
     */
    interface UsageTrendRow {
        String getDate();
        long getRequests();
        long getInputTokens();
        long getOutputTokens();
        long getTotalTokens();
    }

    /**
     * 模型用量行
     */
    interface ModelUsageRow {
        String getModel();
        long getRequests();
        long getInputTokens();
        long getOutputTokens();
        long getTotalTokens();
    }
}
