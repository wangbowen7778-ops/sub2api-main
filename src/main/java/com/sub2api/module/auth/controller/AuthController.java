package com.sub2api.module.auth.controller;

import com.sub2api.module.auth.model.vo.LoginRequest;
import com.sub2api.module.auth.model.vo.LoginResponse;
import com.sub2api.module.auth.model.vo.RegisterRequest;
import com.sub2api.module.auth.service.AuthService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.common.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "认证", description = "用户认证相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getRealIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return Result.ok(response);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getRealIp(httpRequest);

        // 验证密码强度
        if (!authService.validatePasswordStrength(request.getPassword())) {
            return Result.fail(2031, "密码必须包含字母和数字，且长度不少于8位");
        }

        LoginResponse response = authService.regier(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                clientIp
        );
        return Result.ok(response);
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody String refreshToken) {
        // 移除引号
        refreshToken = refreshToken.replace("\"", "").trim();
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.ok(response);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<Object> getCurrentUser(@RequestAttribute("org.springframework.security.core.Authentication") Object auth) {
        if (auth == null) {
            return Result.fail(2006, "未登录");
        }
        var userDetails = (com.sub2api.module.user.model.entity.User) auth;
        return Result.ok(new Object() {
            public Long getId() { return userDetails.getId(); }
            public String getUsername() { return userDetails.getUsername(); }
            public String getEmail() { return userDetails.getEmail(); }
            public String getRole() { return userDetails.getRole(); }
            public Long getBalance() { return userDetails.getBalance() != null ? userDetails.getBalance().longValue() : 0L; }
        });
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，客户端删除令牌即可
        return Result.ok();
    }
}
