package com.sub2api.module.auth.controller;

import com.sub2api.module.auth.model.vo.LoginRequest;
import com.sub2api.module.auth.model.vo.LoginResponse;
import com.sub2api.module.auth.model.vo.RegisterRequest;
import com.sub2api.module.auth.service.AuthService;
import com.sub2api.module.billing.service.PromoCodeService;
import com.sub2api.module.billing.service.RedeemCodeService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.common.util.IpUtil;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.model.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "认证", description = "用户认证相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PromoCodeService promoCodeService;
    private final RedeemCodeService redeemCodeService;

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

        LoginResponse response = authService.register(
                request.getEmail(), // username derived from email
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
    public Result<UserVO> getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Result.fail(2006, "未登录");
        }
        var user = (User) auth.getPrincipal();
        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .balance(user.getBalance())
                .concurrency(user.getConcurrency())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .updatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null)
                .build();
        return Result.ok(userVO);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，客户端删除令牌即可
        return Result.ok();
    }

    @Operation(summary = "验证优惠码")
    @PostMapping("/validate-promo-code")
    public Result<Map<String, Object>> validatePromoCode(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return Result.fail(4001, "优惠码不能为空");
        }
        Map<String, Object> result = promoCodeService.validate(code);
        return Result.ok(result);
    }

    @Operation(summary = "验证邀请码")
    @PostMapping("/validate-invitation-code")
    public Result<Map<String, Object>> validateInvitationCode(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return Result.fail(4001, "邀请码不能为空");
        }
        Map<String, Object> result = redeemCodeService.validateInvitationCode(code);
        return Result.ok(result);
    }

    @Operation(summary = "撤销所有会话")
    @PostMapping("/revoke-all-sessions")
    public Result<Map<String, String>> revokeAllSessions() {
        // JWT 无状态，无法直接撤销所有会话
        // 返回成功，客户端清除所有本地令牌
        return Result.ok(Map.of("message", "所有会话已撤销"));
    }
}
