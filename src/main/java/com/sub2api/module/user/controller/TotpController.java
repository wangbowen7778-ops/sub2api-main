package com.sub2api.module.user.controller;

import com.sub2api.module.admin.service.SettingService;
import com.sub2api.module.auth.service.TOTPService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.common.service.EmailQueueService;
import com.sub2api.module.common.util.EncryptionUtil;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.model.vo.*;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * TOTP 双因素认证控制器
 * 路径: /user/totp
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@RestController
@RequestMapping("/user/totp")
@RequiredArgsConstructor
@Tag(name = "用户 - TOTP", description = "TOTP 双因素认证接口")
public class TotpController {

    private final UserService userService;
    private final TOTPService totpService;
    private final SettingService settingService;
    private final EmailQueueService emailQueueService;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    /**
     * 获取 TOTP 状态
     * GET /user/totp/status
     */
    @Operation(summary = "获取 TOTP 状态")
    @GetMapping("/status")
    public Result<TotpStatusResponse> getStatus() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        TotpStatusResponse response = TotpStatusResponse.builder()
                .enabled(Boolean.TRUE.equals(user.getTotpEnabled()))
                .featureEnabled(settingService.getBoolean("totp_enabled", false))
                .enabledAt(user.getTotpEnabledAt() != null ?
                        user.getTotpEnabledAt().toEpochSecond() : null)
                .build();

        return Result.ok(response);
    }

    /**
     * 获取验证方式
     * GET /user/totp/verification-method
     */
    @Operation(summary = "获取验证方式")
    @GetMapping("/verification-method")
    public Result<TotpVerificationMethodResponse> getVerificationMethod() {
        String method = settingService.getBoolean("email_verify_enabled", false) ? "email" : "password";
        return Result.ok(new TotpVerificationMethodResponse(method));
    }

    /**
     * 发送验证码
     * POST /user/totp/send-code
     */
    @Operation(summary = "发送验证码")
    @PostMapping("/send-code")
    public Result<Void> sendCode() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        if (!settingService.getBoolean("email_verify_enabled", false)) {
            return Result.fail(1001, "邮件验证未启用");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        String siteName = settingService.getValue("site_name", "Sub2API");
        emailQueueService.EnqueueVerifyCode(user.getEmail(), siteName);

        return Result.ok();
    }

    /**
     * 初始化 TOTP 设置
     * POST /user/totp/setup
     */
    @Operation(summary = "初始化 TOTP 设置")
    @PostMapping("/setup")
    public Result<TotpSetupResponse> initiateSetup(@RequestBody(required = false) TotpSetupRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        // 检查 TOTP 功能是否启用
        if (!settingService.getBoolean("totp_enabled", false)) {
            return Result.fail(3011, "TOTP 功能未启用");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        // 检查是否已经启用
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            return Result.fail(3012, "TOTP 已启用");
        }

        // 验证身份
        if (settingService.getBoolean("email_verify_enabled", false)) {
            // 邮箱验证模式
            if (request == null || request.getEmailCode() == null || request.getEmailCode().isBlank()) {
                return Result.fail(3016, "验证码不能为空");
            }
            // TODO: 验证邮箱验证码
        } else {
            // 密码验证模式
            if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
                return Result.fail(3017, "密码不能为空");
            }
            if (!EncryptionUtil.verifyPassword(request.getPassword(), "", user.getPasswordHash())) {
                return Result.fail(2030, "密码错误");
            }
        }

        // 生成 TOTP 密钥
        String secret = totpService.generateSecret();
        String setupToken = totpService.generateRandomToken();

        // 存储 setup session
        TOTPService.TotpSetupSession session = new TOTPService.TotpSetupSession(secret, setupToken);
        totpService.setSetupSession(userId, session);

        // 生成 otpauth URI
        String qrCodeUrl = totpService.generateOtpauthUri(secret, user.getEmail(), TOTPService.TOTP_ISSUER);

        TotpSetupResponse response = TotpSetupResponse.builder()
                .secret(secret)
                .qrCodeUrl(qrCodeUrl)
                .setupToken(setupToken)
                .countdown(5 * 60) // 5 分钟
                .build();

        return Result.ok(response);
    }

    /**
     * 启用 TOTP
     * POST /user/totp/enable
     */
    @Operation(summary = "启用 TOTP")
    @PostMapping("/enable")
    public Result<Void> enable(@RequestBody TotpEnableRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        // 检查 TOTP 功能是否启用
        if (!settingService.getBoolean("totp_enabled", false)) {
            return Result.fail(3011, "TOTP 功能未启用");
        }

        // 获取 setup session
        TOTPService.TotpSetupSession session = totpService.getSetupSession(userId);
        if (session == null) {
            return Result.fail(3015, "TOTP 设置会话已过期");
        }

        // 验证 setup token
        if (!session.setupToken.equals(request.getSetupToken())) {
            return Result.fail(3015, "TOTP 设置会话已过期");
        }

        // 验证 TOTP 代码
        if (!totpService.verifyCode(session.secret, request.getTotpCode())) {
            return Result.fail(3014, "TOTP 验证码无效");
        }

        // 加密 secret
        String encryptedSecret = EncryptionUtil.aesGcmEncrypt(session.secret,
                settingService.getValue("totp_encryption_key", "default-key-please-change"));

        // 启用 TOTP
        userService.enableTotp(userId, encryptedSecret);

        // 删除 setup session
        totpService.deleteSetupSession(userId);

        return Result.ok();
    }

    /**
     * 禁用 TOTP
     * POST /user/totp/disable
     */
    @Operation(summary = "禁用 TOTP")
    @PostMapping("/disable")
    public Result<Void> disable(@RequestBody(required = false) TotpDisableRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.fail(2006, "未登录或登录已过期");
        }

        User user = userService.findById(userId);
        if (user == null) {
            return Result.fail(3001, "用户不存在");
        }

        // 检查是否已启用
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            return Result.fail(3013, "TOTP 未设置");
        }

        // 验证身份
        if (settingService.getBoolean("email_verify_enabled", false)) {
            // 邮箱验证模式
            if (request == null || request.getEmailCode() == null || request.getEmailCode().isBlank()) {
                return Result.fail(3016, "验证码不能为空");
            }
            // TODO: 验证邮箱验证码
        } else {
            // 密码验证模式
            if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
                return Result.fail(3017, "密码不能为空");
            }
            if (!EncryptionUtil.verifyPassword(request.getPassword(), "", user.getPasswordHash())) {
                return Result.fail(2030, "密码错误");
            }
        }

        // 禁用 TOTP
        userService.disableTotp(userId);

        return Result.ok();
    }
}
