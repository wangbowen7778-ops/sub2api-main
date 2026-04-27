package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.service.GroupService;
import com.sub2api.module.apikey.mapper.ApiKeyMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分组管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - 分组", description = "账号分组管理接口")
@RestController
@RequestMapping("/api/v1/admin/groups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GroupAdminController {

    private final GroupService groupService;
    private final ApiKeyMapper apiKeyMapper;

    @Operation(summary = "分页查询分组列表")
    @GetMapping
    public Result<PageResult<Group>> listGroups(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String platform) {

        Page<Group> page = new Page<>(current, size);
        Page<Group> result = groupService.page(page);
        PageResult<Group> pageResult = PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }

    @Operation(summary = "获取分组详情")
    @GetMapping("/{id}")
    public Result<Group> getGroup(@PathVariable Long id) {
        Group group = groupService.findById(id);
        if (group == null) {
            return Result.fail(4020, "分组不存在");
        }
        return Result.ok(group);
    }

    @Operation(summary = "创建分组")
    @PostMapping
    public Result<Group> createGroup(@RequestBody Group group) {
        Group created = groupService.createGroup(group);
        return Result.ok(created);
    }

    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public Result<Void> updateGroup(@PathVariable Long id, @RequestBody Group group) {
        group.setId(id);
        groupService.updateGroup(group);
        return Result.ok();
    }

    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return Result.ok();
    }

    @Operation(summary = "获取平台的分组列表")
    @GetMapping("/platform/{platform}")
    public Result<List<Group>> listByPlatform(@PathVariable String platform) {
        List<Group> groups = groupService.listByPlatform(platform);
        return Result.ok(groups);
    }

    @Operation(summary = "获取分组下的API Keys")
    @GetMapping("/{id}/api-keys")
    public Result<PageResult<Map<String, Object>>> getGroupApiKeys(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        // Check group exists
        Group group = groupService.findById(id);
        if (group == null) {
            return Result.fail(4020, "分组不存在");
        }

        // Query API keys by group ID
        Page<ApiKey> page = new Page<>(current, size);
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getGroupId, id)
                .isNull(ApiKey::getDeletedAt)
                .orderByDesc(ApiKey::getCreatedAt);

        Page<ApiKey> result = apiKeyMapper.selectPage(page, wrapper);

        List<Map<String, Object>> records = result.getRecords().stream()
                .map(key -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", key.getId());
                    map.put("user_id", key.getUserId());
                    map.put("key", key.getKey());
                    map.put("name", key.getName());
                    map.put("status", key.getStatus());
                    map.put("created_at", key.getCreatedAt());
                    map.put("last_used_at", key.getLastUsedAt());
                    return map;
                })
                .collect(Collectors.toList());

        PageResult<Map<String, Object>> pageResult = PageResult.of(result.getTotal(), records, result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }
}
