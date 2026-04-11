package com.sub2api.module.auth.service;

import com.sub2api.module.auth.model.vo.LoginRequest;
import com.sub2api.module.auth.model.vo.LoginResponse;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.util.EncryptionUtil;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final TOTPService totpService;

    /**
     * 用户名密码登录
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 查找用户
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAIL, "用户名或密码错误");
        }

        // 验证密码
        String passwordHash = EncryptionUtil.hashPassword(request.getPassword(), "");
        if (!passwordHash.equals(user.getPasswordHash())) {
            log.warn("密码错误: username={}, ip={}", request.getUsername(), clientIp);
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        // 检查用户状态
        userService.checkUserStatus(user);

        // 如果启用了 TOTP，需要验证
        if (Boolean.TRUE.equals(user.getTotpEnabled()) && request.getTotpCode() == null) {
            return LoginResponse.builder()
                    .requireMfa(true)
                    .userId(user.getId())
                    .build();
        }

        if (Boolean.TRUE.equals(user.getTotpEnabled()) && request.getTotpCode() != null) {
            if (!totpService.verifyCode(user.getTotpSecretEncrypted(), request.getTotpCode())) {
                throw new BusinessException(ErrorCode.AUTH_MFA_INVALID);
            }
        }

        // 生成令牌
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // 更新登录信息
        userService.updateLoginInfo(user.getId(), clientIp);

        log.info("用户登录成功: username={}, ip={}", user.getUsername(), clientIp);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    /**
     * 刷新令牌
     */
    public LoginResponse refreshToken(String refreshToken) {
        try {
            var claims = jwtService.validateRefreshToken(refreshToken);
            Long userId = claims.get("userId", Long.class);

            User user = userService.findById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_INVALID);
            }

            userService.checkUserStatus(user);

            String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
            String newRefreshToken = jwtService.generateRefreshToken(user.getId());

            return LoginResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .build();
        } catch (Exception e) {
            log.warn("刷新令牌失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_INVALID, "令牌刷新失败");
        }
    }

    /**
     * 注册新用户
     */
    public LoginResponse register(String username, String email, String password, String registerIp) {
        // 生成密码哈希
        String passwordHash = EncryptionUtil.hashPassword(password, "");

        // 创建用户
        User user = userService.createUser(email, passwordHash, "user", null);
        user.setUsername(username);
        userService.updateUser(user);

        // 生成令牌
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("用户注册成功: username={}, ip={}", username, registerIp);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    /**
     * 验证密码强度
     */
    public boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        // 至少包含字母和数字
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    /**
     * 获取用户信息
     */
    public User getUserInfo(Long userId) {
        User user = userService.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
