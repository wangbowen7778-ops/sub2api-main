package com.sub2api.module.admin.controller;

import com.sub2api.module.admin.model.entity.Setting;
import com.sub2api.module.admin.service.SettingService;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设置管理控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - Settings", description = "系统设置管理接口")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SettingAdminController {

    private final SettingService settingService;

    @Operation(summary = "获取设置值")
    @GetMapping("/{key}")
    public Result<Map<String, String>> getSetting(@PathVariable String key) {
        String value = settingService.getValue(key);
        return Result.success(Map.of("key", key, "value", value != null ? value : ""));
    }

    @Operation(summary = "获取所有设置")
    @GetMapping
    public Result<Map<String, String>> getAllSettings() {
        Map<String, String> settings = settingService.getAllSettings();
        return Result.success(settings);
    }

    @Operation(summary = "按分类获取设置")
    @GetMapping("/category/{category}")
    public Result<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        Map<String, String> settings = settingService.getSettingsByCategory(category);
        return Result.success(settings);
    }

    @Operation(summary = "设置值")
    @PutMapping("/{key}")
    public Result<Void> setSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        settingService.setValue(key, value);
        return Result.success();
    }

    @Operation(summary = "批量设置值")
    @PutMapping
    public Result<Void> setSettings(@RequestBody Map<String, String> settings) {
        settingService.setValues(settings);
        return Result.success();
    }

    @Operation(summary = "删除设置")
    @DeleteMapping("/{key}")
    public Result<Void> deleteSetting(@PathVariable String key) {
        settingService.deleteSetting(key);
        return Result.success();
    }

    @Operation(summary = "清除所有设置缓存")
    @PostMapping("/cache/clear")
    public Result<Void> clearCache() {
        settingService.clearCache();
        return Result.success();
    }

    @Operation(summary = "获取常用设置")
    @GetMapping("/common")
    public Result<Map<String, String>> getCommonSettings() {
        return Result.success(Map.of(
                "registration_enabled", String.valueOf(settingService.isRegistrationEnabled()),
                "maintenance_mode", String.valueOf(settingService.isMaintenanceMode()),
                "default_group_id", String.valueOf(settingService.getDefaultGroupId()),
                "default_subscription_group_id", String.valueOf(settingService.getDefaultSubscriptionGroupId() != null ? settingService.getDefaultSubscriptionGroupId() : ""),
                "claude_code_version_min", settingService.getClaudeCodeVersionMin() != null ? settingService.getClaudeCodeVersionMin() : "",
                "claude_code_version_max", settingService.getClaudeCodeVersionMax() != null ? settingService.getClaudeCodeVersionMax() : ""
        ));
    }
}
