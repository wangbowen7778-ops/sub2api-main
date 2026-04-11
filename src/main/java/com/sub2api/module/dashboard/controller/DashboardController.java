package com.sub2api.module.dashboard.controller;

import com.sub2api.module.dashboard.model.vo.*;
import com.sub2api.module.dashboard.service.DashboardService;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dashboard 统计控制器
 */
@Tag(name = "管理后台 - Dashboard", description = "Dashboard统计接口")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取仪表盘概览")
    @GetMapping("/stats")
    public Result<DashboardStats> getDashboardStats() {
        DashboardStats stats = dashboardService.getDashboardStats();
        return Result.ok(stats);
    }

    @Operation(summary = "获取用量趋势")
    @GetMapping("/trend")
    public Result<List<TrendDataPoint>> getUsageTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String model) {

        List<TrendDataPoint> trend = dashboardService.getUsageTrend(
                startTime, endTime, granularity, userId, apiKeyId, accountId, groupId, model);
        return Result.ok(trend);
    }

    @Operation(summary = "获取模型统计")
    @GetMapping("/models")
    public Result<List<ModelStat>> getModelStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String model) {

        List<ModelStat> stats = dashboardService.getModelStats(
                startTime, endTime, userId, apiKeyId, accountId, groupId, model);
        return Result.ok(stats);
    }

    @Operation(summary = "获取分组统计")
    @GetMapping("/groups")
    public Result<List<GroupStat>> getGroupStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long groupId) {

        List<GroupStat> stats = dashboardService.getGroupStats(
                startTime, endTime, userId, apiKeyId, accountId, groupId);
        return Result.ok(stats);
    }

    @Operation(summary = "获取分组用量摘要")
    @GetMapping("/groups/summary")
    public Result<List<GroupUsageSummary>> getGroupUsageSummary() {
        List<GroupUsageSummary> summary = dashboardService.getGroupUsageSummary(LocalDateTime.now().withLocalTime());
        return Result.ok(summary);
    }

    @Operation(summary = "获取用户用量趋势")
    @GetMapping("/users/trend")
    public Result<List<UserUsageTrendPoint>> getUserUsageTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "10") int limit) {

        List<UserUsageTrendPoint> trend = dashboardService.getUserUsageTrend(
                startTime, endTime, granularity, limit);
        return Result.ok(trend);
    }

    @Operation(summary = "获取用户消费排名")
    @GetMapping("/users/ranking")
    public Result<UserSpendingRankingResponse> getUserSpendingRanking(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit) {

        UserSpendingRankingResponse ranking = dashboardService.getUserSpendingRanking(
                startTime, endTime, limit);
        return Result.ok(ranking);
    }

    @Operation(summary = "刷新仪表盘缓存")
    @PostMapping("/refresh")
    public Result<Void> refreshCache() {
        dashboardService.invalidateCache();
        dashboardService.refreshDashboardStatsAsync();
        return Result.ok();
    }
}
