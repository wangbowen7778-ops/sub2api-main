package com.sub2api.module.channel.controller;

import com.sub2api.module.channel.model.entity.Channel;
import com.sub2api.module.channel.service.ChannelService;
import com.sub2api.module.channel.service.ChannelService.*;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 渠道管理控制器
 */
@Tag(name = "管理后台 - 渠道", description = "渠道管理接口")
@RestController
@RequestMapping("/admin/channels")
@RequiredArgsConstructor
public class ChannelAdminController {

    private final ChannelService channelService;

    @Operation(summary = "创建渠道")
    @PostMapping
    public Result<Channel> create(@RequestBody CreateChannelInput input) {
        Channel channel = channelService.create(input);
        return Result.ok(channel);
    }

    @Operation(summary = "获取渠道详情")
    @GetMapping("/{id}")
    public Result<Channel> getById(@PathVariable Long id) {
        Channel channel = channelService.getById(id);
        return Result.ok(channel);
    }

    @Operation(summary = "更新渠道")
    @PutMapping("/{id}")
    public Result<Channel> update(@PathVariable Long id, @RequestBody UpdateChannelInput input) {
        Channel channel = channelService.update(id, input);
        return Result.ok(channel);
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "获取渠道列表")
    @GetMapping
    public Result<PageResult<Channel>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        PageResult<Channel> result = channelService.list(page, pageSize, status, search);
        return Result.ok(result);
    }

    @Operation(summary = "获取所有活跃渠道")
    @GetMapping("/active")
    public Result<List<Channel>> listActive() {
        List<Channel> channels = channelService.listAll();
        return Result.ok(channels);
    }
}
