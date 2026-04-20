package com.sub2api.module.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sub2api.module.ops.model.entity.OpsErrorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ops 错误日志 Mapper
 */
@Mapper
public interface OpsErrorLogMapper extends BaseMapper<OpsErrorLog> {

    /**
     * 根据时间范围查询错误日志
     */
    @Select("SELECT * FROM ops_error_logs WHERE created_at >= #{startTime} AND created_at < #{endTime} ORDER BY created_at DESC")
    List<OpsErrorLog> selectByTimeRange(@Param("startTime") OffsetDateTime startTime,
                                        @Param("endTime") OffsetDateTime endTime);

    /**
     * 根据时间范围和平台查询错误日志
     */
    @Select("SELECT * FROM ops_error_logs WHERE created_at >= #{startTime} AND created_at < #{endTime} AND platform = #{platform} ORDER BY created_at DESC")
    List<OpsErrorLog> selectByTimeRangeAndPlatform(@Param("startTime") OffsetDateTime startTime,
                                                   @Param("endTime") OffsetDateTime endTime,
                                                   @Param("platform") String platform);

    /**
     * 根据时间范围和分组ID查询错误日志
     */
    @Select("SELECT * FROM ops_error_logs WHERE created_at >= #{startTime} AND created_at < #{endTime} AND group_id = #{groupId} ORDER BY created_at DESC")
    List<OpsErrorLog> selectByTimeRangeAndGroupId(@Param("startTime") OffsetDateTime startTime,
                                                   @Param("endTime") OffsetDateTime endTime,
                                                   @Param("groupId") Long groupId);

    /**
     * 统计错误数量
     */
    @Select("SELECT COUNT(*) FROM ops_error_logs WHERE created_at >= #{startTime} AND created_at < #{endTime}")
    long countByTimeRange(@Param("startTime") OffsetDateTime startTime,
                          @Param("endTime") OffsetDateTime endTime);

    /**
     * 根据状态码统计错误
     */
    @Select("SELECT COUNT(*) FROM ops_error_logs WHERE created_at >= #{startTime} AND created_at < #{endTime} AND status_code = #{statusCode}")
    long countByStatusCode(@Param("startTime") OffsetDateTime startTime,
                           @Param("endTime") OffsetDateTime endTime,
                           @Param("statusCode") Integer statusCode);
}
