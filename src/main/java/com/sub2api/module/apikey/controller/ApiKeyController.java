package com.sub2api.module.apikey.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.*;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.auth.service.AuthContextService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.group.model.entity.AppGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户侧 API Key 控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "用户 - API Key", description = "用户 API Key 管理接口")
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuthContextService authContextService;

    @Operation(summary = "获取 API Key 列表")
    @GetMapping
    public Result<PageResult<ApiKeyResponse>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String search,
            @Parameter(description = "状态过滤") @RequestParam(required = false) String status,
            @Parameter(description = "分组ID过滤") @RequestParam(required = false) Long groupId) {

        Long userId = authContextService.getCurrentUserId();
        PageResult<ApiKey> pageResult = apiKeyService.listByUserId(userId, page, pageSize, search, status, groupId);

        List<ApiKeyResponse> records = pageResult.getRecords().stream()
                .map(this::toApiKeyResponse)
                .collect(Collectors.toList());

        PageResult<ApiKeyResponse> result = PageResult.of(
                pageResult.getTotal(),
                records,
                pageResult.getCurrent(),
                pageResult.getSize()
        );

        return Result.ok(result);
    }

    @Operation(summary = "获取单个 API Key")
    @GetMapping("/{id}")
    public Result<ApiKeyResponse> getById(@PathVariable Long id) {
        Long userId = authContextService.getCurrentUserId();

        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.NOT_FOUND);
        }

        // 验证所有权
        if (!apiKey.getUserId().equals(userId)) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.AUTH_FORBIDDEN, "Not authorized to access this API Key");
        }

        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "创建 API Key")
    @PostMapping
    public Result<ApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = authContextService.getCurrentUserId();
        ApiKey apiKey = apiKeyService.createApiKey(userId, request);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "更新 API Key")
    @PutMapping("/{id}")
    public Result<ApiKeyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApiKeyRequest request) {
        Long userId = authContextService.getCurrentUserId();
        ApiKey apiKey = apiKeyService.updateApiKey(id, userId, request);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "删除 API Key")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = authContextService.getCurrentUserId();
        apiKeyService.deleteApiKey(id, userId);
        return Result.ok();
    }

    @Operation(summary = "获取用户可用的分组列表")
    @GetMapping("/available-groups")
    public Result<List<GroupResponse>> getAvailableGroups() {
        Long userId = authContextService.getCurrentUserId();
        List<AppGroup> groups = apiKeyService.getAvailableGroups(userId);

        List<GroupResponse> records = groups.stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());

        return Result.ok(records);
    }

    @Operation(summary = "获取用户专属分组倍率配置")
    @GetMapping("/group-rates")
    public Result<Map<Long, Double>> getUserGroupRates() {
        Long userId = authContextService.getCurrentUserId();
        Map<Long, Double> rates = apiKeyService.getUserGroupRates(userId);
        return Result.ok(rates);
    }

    /**
     * 转换为 API Key 响应
     */
    private ApiKeyResponse toApiKeyResponse(ApiKey apiKey) {
        if (apiKey == null) {
            return null;
        }

        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(apiKey.getId());
        response.setUserId(apiKey.getUserId());
        response.setKey(apiKey.getKey());
        response.setName(apiKey.getName());
        response.setGroupId(apiKey.getGroupId());
        response.setStatus(apiKey.getStatus());
        response.setIpWhitelist(apiKey.getIpWhitelist());
        response.setIpBlacklist(apiKey.getIpBlacklist());
        response.setLastUsedAt(apiKey.getLastUsedAt());
        response.setQuota(apiKey.getQuota());
        response.setQuotaUsed(apiKey.getQuotaUsed());
        response.setExpiresAt(apiKey.getExpiresAt());
        response.setCreatedAt(apiKey.getCreatedAt());
        response.setUpdatedAt(apiKey.getUpdatedAt());
        response.setRateLimit5h(apiKey.getRateLimit5h());
        response.setRateLimit1d(apiKey.getRateLimit1d());
        response.setRateLimit7d(apiKey.getRateLimit7d());
        response.setUsage5h(apiKey.getUsage5h());
        response.setUsage1d(apiKey.getUsage1d());
        response.setUsage7d(apiKey.getUsage7d());
        response.setWindow5hStart(apiKey.getWindow5hStart());
        response.setWindow1dStart(apiKey.getWindow1dStart());
        response.setWindow7dStart(apiKey.getWindow7dStart());

        return response;
    }

    /**
     * 转换为分组响应
     */
    private GroupResponse toGroupResponse(AppGroup group) {
        if (group == null) {
            return null;
        }

        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setPlatform(group.getPlatform());
        response.setRateMultiplier(group.getRateMultiplier());
        response.setIsExclusive(group.getIsExclusive());
        response.setStatus(group.getStatus());
        response.setSubscriptionType(group.getSubscriptionType());
        response.setDailyLimitUsd(group.getDailyLimitUsd());
        response.setWeeklyLimitUsd(group.getWeeklyLimitUsd());
        response.setMonthlyLimitUsd(group.getMonthlyLimitUsd());

        return response;
    }
}