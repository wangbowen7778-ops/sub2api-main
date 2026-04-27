package com.sub2api.module.admin.controller;

import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.service.GroupService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin API Key Management Controller
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - API Key", description = "Admin API Key Management API")
@RestController
@RequestMapping("/api/v1/admin/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final GroupService groupService;

    @Operation(summary = "Query user's API Keys (paginated)")
    @GetMapping("/user/{userId}")
    public Result<PageResult<ApiKeyResponse>> listByUser(
            @PathVariable Long userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer pageSize) {

        PageResult<ApiKey> pageResult = apiKeyService.listByUserId(userId, page, pageSize, null, null, null);

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

    @Operation(summary = "Create API Key (admin)")
    @PostMapping
    public Result<ApiKeyResponse> createApiKey(@RequestBody CreateApiKeyAdminRequest request) {
        Long userId = request.getUserId();
        String name = request.getName();
        Long groupId = request.getGroupId();

        ApiKey apiKey = apiKeyService.adminCreateApiKey(userId, name, groupId);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "Update API Key status")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        apiKeyService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "Delete API Key (admin)")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        // Admin delete doesn't need ownership validation
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.NOT_FOUND);
        }
        apiKeyService.adminDeleteApiKey(id);
        return Result.ok();
    }

    @Operation(summary = "Admin update API Key group binding")
    @PutMapping("/{id}")
    public Result<AdminUpdateGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateGroupRequest request) {

        ApiKeyService.AdminUpdateGroupResult result = apiKeyService.adminUpdateGroup(id, request.getGroupId());

        AdminUpdateGroupResponse response = AdminUpdateGroupResponse.builder()
                .apiKey(toApiKeyResponse(result.getApiKey()))
                .autoGrantedGroupAccess(result.getAutoGrantedGroupAccess())
                .grantedGroupId(result.getGrantedGroupId())
                .grantedGroupName(result.getGrantedGroupName())
                .build();

        return Result.ok(response);
    }

    /**
     * Admin create API Key request
     */
    @lombok.Data
    public static class CreateApiKeyAdminRequest {
        private Long userId;
        private String name;
        private Long groupId;
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
