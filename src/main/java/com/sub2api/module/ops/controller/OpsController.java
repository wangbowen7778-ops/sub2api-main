package com.sub2api.module.ops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.ops.mapper.OpsErrorLogMapper;
import com.sub2api.module.ops.model.entity.OpsErrorLog;
import com.sub2api.module.ops.model.vo.OpsDashboardOverview;
import com.sub2api.module.ops.service.OpsService;
import com.sub2api.module.ops.service.OpsService.OpsDashboardFilter;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ops 监控控制器
 */
@Tag(name = "管理后台 - Ops监控", description = "Ops监控接口")
@RestController
@RequestMapping("/api/v1/admin/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;
    private final OpsErrorLogMapper opsErrorLogMapper;

    @Operation(summary = "获取仪表板概览")
    @GetMapping("/dashboard")
    public Result<OpsDashboardOverview> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Long groupId) {

        OpsDashboardFilter filter = new OpsDashboardFilter();
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        filter.setPlatform(platform);
        filter.setGroupId(groupId);

        OpsDashboardOverview overview = opsService.getDashboardOverview(filter);
        return Result.ok(overview);
    }

    @Operation(summary = "获取错误日志列表")
    @GetMapping("/errors")
    public Result<List<OpsErrorLog>> getErrorLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false, defaultValue = "100") int limit) {

        LambdaQueryWrapper<OpsErrorLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(OpsErrorLog::getCreatedAt, startTime)
                .lt(OpsErrorLog::getCreatedAt, endTime)
                .orderByDesc(OpsErrorLog::getCreatedAt)
                .last("LIMIT " + limit);

        if (platform != null && !platform.isBlank()) {
            wrapper.eq(OpsErrorLog::getPlatform, platform);
        }

        List<OpsErrorLog> errorLogs = opsErrorLogMapper.selectList(wrapper);
        return Result.ok(errorLogs);
    }

    @Operation(summary = "获取系统指标")
    @GetMapping("/metrics")
    public Result<OpsDashboardOverview.SystemMetrics> getSystemMetrics() {
        OpsDashboardOverview.SystemMetrics metrics = opsService.getLatestSystemMetrics(1);
        return Result.ok(metrics);
    }

    @Operation(summary = "获取任务心跳")
    @GetMapping("/heartbeats")
    public Result<List<OpsDashboardOverview.JobHeartbeat>> getJobHeartbeats() {
        List<OpsDashboardOverview.JobHeartbeat> heartbeats = opsService.getJobHeartbeats();
        return Result.ok(heartbeats);
    }

    @Operation(summary = "记录错误日志")
    @PostMapping("/errors")
    public Result<Void> recordError(@RequestBody OpsErrorLog errorLog) {
        opsService.recordError(errorLog);
        return Result.ok();
    }
}
