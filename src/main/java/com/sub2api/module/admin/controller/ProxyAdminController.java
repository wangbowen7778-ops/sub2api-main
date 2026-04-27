package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.model.entity.Proxy;
import com.sub2api.module.account.service.ProxyConfigService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代理配置管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "管理 - 代理", description = "代理配置管理接口")
@RestController
@RequestMapping("/api/v1/admin/proxies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProxyAdminController {

    private final ProxyConfigService proxyService;

    @Operation(summary = "分页查询代理列表")
    @GetMapping
    public Result<PageResult<Proxy>> listProxies(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String status) {

        Page<Proxy> page = new Page<>(current, size);
        Page<Proxy> result = proxyService.page(page);
        PageResult<Proxy> pageResult = PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }

    @Operation(summary = "获取代理详情")
    @GetMapping("/{id}")
    public Result<Proxy> getProxy(@PathVariable Long id) {
        Proxy proxy = proxyService.findById(id);
        if (proxy == null) {
            return Result.fail(7030, "代理配置不存在");
        }
        return Result.ok(proxy);
    }

    @Operation(summary = "创建代理配置")
    @PostMapping
    public Result<Proxy> createProxy(@RequestBody Proxy proxy) {
        Proxy created = proxyService.createProxy(proxy);
        return Result.ok(created);
    }

    @Operation(summary = "更新代理配置")
    @PutMapping("/{id}")
    public Result<Void> updateProxy(@PathVariable Long id, @RequestBody Proxy proxy) {
        proxy.setId(id);
        proxyService.updateProxy(proxy);
        return Result.ok();
    }

    @Operation(summary = "删除代理配置")
    @DeleteMapping("/{id}")
    public Result<Void> deleteProxy(@PathVariable Long id) {
        proxyService.deleteProxy(id);
        return Result.ok();
    }

    @Operation(summary = "获取所有活跃代理")
    @GetMapping("/active")
    public Result<List<Proxy>> listActiveProxies() {
        List<Proxy> proxies = proxyService.listActive();
        return Result.ok(proxies);
    }
}
