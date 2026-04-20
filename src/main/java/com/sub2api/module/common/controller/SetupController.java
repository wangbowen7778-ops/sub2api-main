package com.sub2api.module.common.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.admin.mapper.SettingMapper;
import com.sub2api.module.admin.model.entity.Setting;
import com.sub2api.module.user.mapper.UserMapper;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setup 向导控制器 - 前端初始化需要
 */
@RestController
@RequestMapping("/setup")
@RequiredArgsConstructor
@Tag(name = "Setup", description = "安装向导接口")
public class SetupController {

    private final UserMapper userMapper;
    private final SettingMapper settingMapper;

    @GetMapping("/status")
    @Operation(summary = "获取安装状态")
    public Result<SetupStatusVO> getStatus() {
        // 检查是否已有用户
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>().isNull(User::getDeletedAt));
        // 检查是否已有设置（表示已初始化）
        Setting setupComplete = settingMapper.selectByKey("setup_complete");

        boolean needsSetup = userCount == 0 || setupComplete == null;

        SetupStatusVO vo = new SetupStatusVO();
        vo.setNeedsSetup(needsSetup);
        vo.setStep(needsSetup ? "welcome" : "complete");

        return Result.ok(vo);
    }

    @lombok.Data
    public static class SetupStatusVO {
        private boolean needsSetup;
        private String step;
    }
}
