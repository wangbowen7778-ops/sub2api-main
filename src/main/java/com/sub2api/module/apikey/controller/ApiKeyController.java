package com.sub2api.module.apikey.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.*;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User-side API Key Controller
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "User - API Key", description = "User API Key management API")
@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    @Operation(summary = "Get API Key list")
    @GetMapping
    public Result<PageResult<ApiKeyResponse>> list(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "Search keyword") @RequestParam(required = false) String search,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Group ID filter") @RequestParam(required = false) Long groupId) {

        Long userId = userService.getCurrentUserId();
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

    @Operation(summary = "Get single API Key")
    @GetMapping("/{id}")
    public Result<ApiKeyResponse> getById(@PathVariable Long id) {
        Long userId = userService.getCurrentUserId();

        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.NOT_FOUND);
        }

        // Validate ownership
        if (!apiKey.getUserId().equals(userId)) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.AUTH_FORBIDDEN, "Not authorized to access this API Key");
        }

        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "Create API Key")
    @PostMapping
    public Result<ApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = userService.getCurrentUserId();
        ApiKey apiKey = apiKeyService.createApiKey(userId, request);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "Update API Key")
    @PutMapping("/{id}")
    public Result<ApiKeyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApiKeyRequest request) {
        Long userId = userService.getCurrentUserId();
        ApiKey apiKey = apiKeyService.updateApiKey(id, userId, request);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "Delete API Key")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = userService.getCurrentUserId();
        apiKeyService.deleteApiKey(id, userId);
        return Result.ok();
    }

    /**
     * Convert to API Key response
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
}
