package com.sub2api.module.admin.controller;

import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API Key 管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - API Key", description = "API Key 管理接口")
@RestController
@RequestMapping("/admin/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    @Operation(summary = "查询用户的 API Keys")
    @GetMapping("/user/{userId}")
    public Result<List<ApiKey>> listByUser(@PathVariable Long userId) {
        List<ApiKey> apiKeys = apiKeyService.listByUserId(userId);
        // Clear sensitive info - mask the key value
        apiKeys.forEach(k -> k.setKey("***"));
        return Result.ok(apiKeys);
    }

    @Operation(summary = "创建 API Key (管理员)")
    @PostMapping
    public Result<ApiKey> createApiKey(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String name = params.get("name").toString();
        Long groupId = params.get("groupId") != null ? Long.valueOf(params.get("groupId").toString()) : null;

        ApiKey apiKey = apiKeyService.createApiKey(userId, name, groupId);
        return Result.ok(apiKey);
    }

    @Operation(summary = "更新 API Key 状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        apiKeyService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除 API Key")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        apiKeyService.deleteApiKey(id);
        return Result.ok();
    }
}
