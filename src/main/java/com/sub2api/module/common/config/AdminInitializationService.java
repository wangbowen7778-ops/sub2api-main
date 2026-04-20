package com.sub2api.module.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sub2api.module.common.util.EncryptionUtil;
import com.sub2api.module.user.mapper.UserMapper;
import com.sub2api.module.user.model.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Admin initialization service
 * Creates the initial admin user on first startup if configured and no admin exists.
 * Mirrors the Go backend's setup behavior in internal/setup/setup.go
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializationService {

    private final UserMapper userMapper;
    private final AdminConfig adminConfig;

    private static final String ROLE_ADMIN = "admin";
    private static final String STATUS_ACTIVE = "active";

    @PostConstruct
    public void initAdminUser() {
        try {
            // Check if admin config is provided
            if (adminConfig.getEmail() == null || adminConfig.getEmail().isBlank()) {
                log.info("Admin email not configured, skipping admin creation");
                return;
            }

            // Check if any admin already exists
            long adminCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRole, ROLE_ADMIN)
                            .isNull(User::getDeletedAt)
            );

            if (adminCount > 0) {
                // Admin exists - check if we should promote the configured email to admin
                User existingUser = userMapper.selectOne(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getEmail, adminConfig.getEmail())
                                .isNull(User::getDeletedAt)
                );

                if (existingUser != null && !ROLE_ADMIN.equals(existingUser.getRole())) {
                    // Promote existing user to admin
                    existingUser.setRole(ROLE_ADMIN);
                    userMapper.updateById(existingUser);
                    log.info("Promoted user to admin: {}", adminConfig.getEmail());
                } else if (existingUser != null && ROLE_ADMIN.equals(existingUser.getRole())) {
                    log.info("Configured user is already admin: {}", adminConfig.getEmail());
                } else {
                    log.info("Admin user already exists, configured email not found in database");
                }
                return;
            }

            // No admin exists - create one
            // Determine password
            String password = adminConfig.getPassword();
            if (password == null || password.isBlank()) {
                password = generateSecret(16);
                log.info("Generated admin password (one-time): {}", password);
                log.info("IMPORTANT: Save this password! It will not be shown again.");
            }

            // Create admin user
            String passwordHash = EncryptionUtil.hashPassword(password, "");

            User admin = new User();
            admin.setEmail(adminConfig.getEmail());
            admin.setPasswordHash(passwordHash);
            admin.setRole(ROLE_ADMIN);
            admin.setStatus(STATUS_ACTIVE);
            admin.setBalance(java.math.BigDecimal.ZERO);
            admin.setConcurrency(10); // default admin concurrency

            userMapper.insert(admin);

            log.info("Admin user created: {}", adminConfig.getEmail());

        } catch (Exception e) {
            log.error("Failed to create admin user: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate a random secret string
     */
    private String generateSecret(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}
