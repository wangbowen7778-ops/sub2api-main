package com.sub2api.module.admin.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.model.vo.*;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.group.model.entity.AppGroup;
import com.sub2api.module.group.service.GroupService;
import com.sub2api.module.user.model.entity.User;
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
 * 管理员 API Key 管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - API Key", description = "管理员 API Key 管理接口")
@RestController
@RequestMapping("/admin/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final GroupService groupService;

    @Operation(summary = "查询用户的 API Keys (分页)")
    @GetMapping("/user/{userId}")
    public Result<PageResult<ApiKeyResponse>> listByUser(
            @PathVariable Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize) {

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

    @Operation(summary = "创建 API Key (管理员)")
    @PostMapping
    public Result<ApiKeyResponse> createApiKey(@RequestBody CreateApiKeyAdminRequest request) {
        Long userId = request.getUserId();
        String name = request.getName();
        Long groupId = request.getGroupId();

        ApiKey apiKey = apiKeyService.adminCreateApiKey(userId, name, groupId);
        return Result.ok(toApiKeyResponse(apiKey));
    }

    @Operation(summary = "更新 API Key 状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        apiKeyService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除 API Key (管理员)")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        // 管理员删除不需要验证所有权，直接删除
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return Result.fail(com.sub2api.module.common.model.enums.ErrorCode.NOT_FOUND);
        }
        apiKeyService.adminDeleteApiKey(id);
        return Result.ok();
    }

    @Operation(summary = "管理员更新 API Key 分组绑定")
    @PutMapping("/{id}")
    public Result<AdminUpdateGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateGroupRequest request) {

        AdminUpdateGroupResult result = apiKeyService.adminUpdateGroup(id, request.getGroupId());

        AdminUpdateGroupResponse response = AdminUpdateGroupResponse.builder()
                .apiKey(toApiKeyResponse(result.getApiKey()))
                .autoGrantedGroupAccess(result.getAutoGrantedGroupAccess())
                .grantedGroupId(result.getGrantedGroupId())
                .grantedGroupName(result.getGrantedGroupName())
                .build();

        return Result.ok(response);
    }

    /**
     * 管理员创建 API Key 请求
     */
    @lombok.Data
    public static class CreateApiKeyAdminRequest {
        private Long userId;
        private String name;
        private Long groupId;
    }

    /**
     * 管理员更新分组结果
     */
    @lombok.Data
    @lombok.Builder
    public static class AdminUpdateGroupResult {
        private ApiKey apiKey;
        private Boolean autoGrantedGroupAccess;
        private Long grantedGroupId;
        private String grantedGroupName;
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
}