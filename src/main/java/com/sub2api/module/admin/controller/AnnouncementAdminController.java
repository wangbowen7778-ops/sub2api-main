package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.admin.model.entity.Announcement;
import com.sub2api.module.admin.service.AnnouncementService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - 公告", description = "公告管理接口")
@RestController
@RequestMapping("/admin/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnnouncementAdminController {

    private final AnnouncementService announcementService;

    @Operation(summary = "分页查询公告列表")
    @GetMapping
    public Result<PageResult<Announcement>> listAnnouncements(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String status) {

        Page<Announcement> page = new Page<>(current, size);
        Page<Announcement> result = announcementService.page(page);
        PageResult<Announcement> pageResult = PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }

    @Operation(summary = "获取公告详情")
    @GetMapping("/{id}")
    public Result<Announcement> getAnnouncement(@PathVariable Long id) {
        Announcement announcement = announcementService.getById(id);
        if (announcement == null) {
            return Result.fail(7020, "公告不存在");
        }
        return Result.ok(announcement);
    }

    @Operation(summary = "创建公告")
    @PostMapping
    public Result<Announcement> createAnnouncement(@RequestBody Announcement announcement) {
        Announcement created = announcementService.createAnnouncement(announcement);
        return Result.ok(created);
    }

    @Operation(summary = "更新公告")
    @PutMapping("/{id}")
    public Result<Void> updateAnnouncement(@PathVariable Long id, @RequestBody Announcement announcement) {
        announcement.setId(id);
        announcementService.updateAnnouncement(announcement);
        return Result.ok();
    }

    @Operation(summary = "发布公告")
    @PostMapping("/{id}/publish")
    public Result<Void> publishAnnouncement(@PathVariable Long id) {
        announcementService.publish(id, null);
        return Result.ok();
    }

    @Operation(summary = "归档公告")
    @PostMapping("/{id}/archive")
    public Result<Void> archiveAnnouncement(@PathVariable Long id) {
        announcementService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAnnouncement(@PathVariable Long id) {
        announcementService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "获取当前有效公告")
    @GetMapping("/active")
    public Result<List<Announcement>> listActiveAnnouncements() {
        List<Announcement> announcements = announcementService.listActive();
        return Result.ok(announcements);
    }
}
