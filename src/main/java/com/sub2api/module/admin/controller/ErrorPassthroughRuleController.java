package com.sub2api.module.admin.controller;

import com.sub2api.module.admin.model.entity.ErrorPassthroughRule;
import com.sub2api.module.admin.service.ErrorPassthroughRuleService;
import com.sub2api.module.common.model.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 错误透传规则管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/error-passthrough-rules")
@RequiredArgsConstructor
public class ErrorPassthroughRuleController {

    private final ErrorPassthroughRuleService errorPassthroughRuleService;

    /**
     * 获取所有规则
     * GET /admin/error-passthrough-rules
     */
    @GetMapping
    public Result<List<ErrorPassthroughRule>> list() {
        List<ErrorPassthroughRule> rules = errorPassthroughRuleService.listAll();
        return Result.ok(rules);
    }

    /**
     * 获取规则
     * GET /admin/error-passthrough-rules/:id
     */
    @GetMapping("/{id}")
    public Result<ErrorPassthroughRule> get(@PathVariable Long id) {
        ErrorPassthroughRule rule = errorPassthroughRuleService.getById(id);
        return Result.ok(rule);
    }

    /**
     * 创建规则
     * POST /admin/error-passthrough-rules
     */
    @PostMapping
    public Result<ErrorPassthroughRule> create(@RequestBody ErrorPassthroughRule rule) {
        ErrorPassthroughRule created = errorPassthroughRuleService.create(rule);
        return Result.ok(created);
    }

    /**
     * 更新规则
     * PUT /admin/error-passthrough-rules/:id
     */
    @PutMapping("/{id}")
    public Result<ErrorPassthroughRule> update(@PathVariable Long id, @RequestBody ErrorPassthroughRule update) {
        ErrorPassthroughRule rule = errorPassthroughRuleService.update(id, update);
        return Result.ok(rule);
    }

    /**
     * 删除规则
     * DELETE /admin/error-passthrough-rules/:id
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        errorPassthroughRuleService.delete(id);
        return Result.ok();
    }

    /**
     * 使缓存失效
     * POST /admin/error-passthrough-rules/cache/invalidate
     */
    @PostMapping("/cache/invalidate")
    public Result<Void> invalidateCache() {
        errorPassthroughRuleService.invalidateCache();
        return Result.ok();
    }

    /**
     * 重新加载缓存
     * POST /admin/error-passthrough-rules/cache/reload
     */
    @PostMapping("/cache/reload")
    public Result<Void> reloadCache() {
        errorPassthroughRuleService.reloadCache();
        return Result.ok();
    }
}
