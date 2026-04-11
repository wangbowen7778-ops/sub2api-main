package com.sub2api.module.user.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.model.entity.UsageLog;
import com.sub2api.module.billing.service.UsageLogService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户个人中心控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "用户", description = "用户个人接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final UsageLogService usageLogService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<User> getCurrentUser(@RequestAttribute("org.springframework.security.core.Authentication") Object auth) {
        if (auth == null) {
            return Result.fail(2006, "未登录");
        }

        Long userId = null;
        if (auth instanceof User user) {
            userId = user.getId();
        } else if (auth instanceof com.sub2api.module.apikey.model.vo.ApiKeyInfo apiKeyInfo) {
            userId = apiKeyInfo.getUserId();
        }

        if (userId == null) {
            return Result.fail(2006, "无法获取用户信息");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        // 清除敏感信息
        user.setPasswordHash(null);
        user.setPasswordSalt(null);
        user.setTotpSecretEncrypted(null);

        return Result.ok(user);
    }

    @Operation(summary = "获取我的 API Keys")
    @GetMapping("/api-keys")
    public Result<List<ApiKey>> getMyApiKeys(@RequestAttribute("org.springframework.security.core.Authentication") Object auth) {
        Long userId = getUserId(auth);
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        List<ApiKey> apiKeys = apiKeyService.listByUserId(userId);
        // 清除敏感信息
        apiKeys.forEach(k -> k.setKey(null));
        return Result.ok(apiKeys);
    }

    @Operation(summary = "创建 API Key")
    @PostMapping("/api-keys")
    public Result<ApiKey> createApiKey(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("org.springframework.security.core.Authentication") Object auth) {

        Long userId = getUserId(auth);
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        String name = (String) params.get("name");
        Long groupId = params.get("groupId") != null ?
                Long.parseLong(params.get("groupId").toString()) : null;

        ApiKey apiKey = apiKeyService.createApiKey(userId, name, groupId);
        return Result.ok(apiKey);
    }

    @Operation(summary = "删除 API Key")
    @DeleteMapping("/api-keys/{id}")
    public Result<Void> deleteApiKey(
            @PathVariable Long id,
            @RequestAttribute("org.springframework.security.core.Authentication") Object auth) {

        Long userId = getUserId(auth);
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        apiKeyService.deleteApiKey(id);
        return Result.ok();
    }

    @Operation(summary = "获取用量记录")
    @GetMapping("/usage")
    public Result<PageResult<UsageLog>> getUsage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endTime,
            @RequestAttribute("org.springframework.security.core.Authentication") Object auth) {

        Long userId = getUserId(auth);
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        PageResult<UsageLog> pageResult = usageLogService.pageByUserId(userId, current, size, platform, model, startTime, endTime);
        return Result.ok(pageResult);
    }

    @Operation(summary = "获取用量统计")
    @GetMapping("/usage/statistics")
    public Result<Map<String, Object>> getUsageStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endTime,
            @RequestAttribute("org.springframework.security.core.Authentication") Object auth) {

        Long userId = getUserId(auth);
        if (userId == null) {
            return Result.fail(2006, "未登录");
        }

        UsageLogService.UsageStatistics stats = usageLogService.getUserStatistics(userId, startTime, endTime);

        Map<String, Object> result = new HashMap<>();
        result.put("inputTokens", stats.inputTokens());
        result.put("outputTokens", stats.outputTokens());
        result.put("totalCost", stats.cost());
        result.put("requestCount", stats.requestCount());
        result.put("startTime", startTime);
        result.put("endTime", endTime);

        return Result.ok(result);
    }

    private Long getUserId(Object auth) {
        if (auth == null) {
            return null;
        }
        if (auth instanceof User user) {
            return user.getId();
        }
        if (auth instanceof com.sub2api.module.apikey.model.vo.ApiKeyInfo apiKeyInfo) {
            return apiKeyInfo.getUserId();
        }
        return null;
    }
}
