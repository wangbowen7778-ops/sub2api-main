package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.service.GroupService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分组管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - 分组", description = "账号分组管理接口")
@RestController
@RequestMapping("/admin/groups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class GroupAdminController {

    private final GroupService groupService;

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
}
