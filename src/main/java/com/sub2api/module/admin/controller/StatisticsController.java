package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.model.entity.UsageLog;
import com.sub2api.module.billing.service.UsageLogService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Statistics controller
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - Statistics", description = "Usage statistics API")
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final UsageLogService usageLogService;
    private final UserService userService;
    private final AccountService accountService;
    private final ApiKeyService apiKeyService;

    @Operation(summary = "Get user usage statistics")
    @GetMapping("/usage/user/{userId}")
    public Result<Map<String, Object>> getUserUsageStatistics(
            @PathVariable Long userId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        OffsetDateTime start = startTime != null ? OffsetDateTime.parse(startTime) : OffsetDateTime.now().minusDays(7);
        OffsetDateTime end = endTime != null ? OffsetDateTime.parse(endTime) : OffsetDateTime.now();

        UsageLogService.UsageStatistics stats = usageLogService.getUserStatistics(userId, start, end);

        Map<String, Object> result = new HashMap<>();
        result.put("inputTokens", stats.inputTokens());
        result.put("outputTokens", stats.outputTokens());
        result.put("totalCost", stats.cost());
        result.put("requestCount", stats.requestCount());
        result.put("startTime", start);
        result.put("endTime", end);

        return Result.ok(result);
    }

    @Operation(summary = "Get system overview statistics")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        // Count users
        long totalUsers = userService.count(new LambdaQueryWrapper<User>().isNull(User::getDeletedAt));

        // Count accounts
        long totalAccounts = accountService.count(new LambdaQueryWrapper<Account>().isNull(Account::getDeletedAt));

        // Count active API Keys
        long activeApiKeys = apiKeyService.count(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getStatus, "active")
                .isNull(ApiKey::getDeletedAt));

        // Count total requests
        LambdaQueryWrapper<UsageLog> usageWrapper = new LambdaQueryWrapper<>();
        long totalRequests = usageLogService.count(usageWrapper);

        // Calculate total cost
        BigDecimal totalCost = BigDecimal.ZERO;
        var usageLogs = usageLogService.list(usageWrapper);
        for (UsageLog log : usageLogs) {
            if (log.getTotalCost() != null) {
                totalCost = totalCost.add(log.getTotalCost());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalAccounts", totalAccounts);
        result.put("totalRequests", totalRequests);
        result.put("totalCost", totalCost);
        result.put("activeApiKeys", activeApiKeys);
        return Result.ok(result);
    }
}
