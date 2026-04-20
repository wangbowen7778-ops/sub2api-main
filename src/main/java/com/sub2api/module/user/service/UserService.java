package com.sub2api.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.user.mapper.UserMapper;
import com.sub2api.module.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * User service
 *
 * @author Alibaba Java Code Guidelines
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;

    /**
     * Find user by email
     */
    public User findByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .isNull(User::getDeletedAt));
    }

    /**
     * Get user by email (alias for findByEmail)
     */
    public User getByEmail(String email) {
        return findByEmail(email);
    }

    /**
     * Find user by username
     */
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .isNull(User::getDeletedAt));
    }

    /**
     * Find user by ID
     */
    public User findById(Long id) {
        User user = getById(id);
        if (user == null || user.getDeletedAt() != null) {
            return null;
        }
        return user;
    }

    /**
     * Get current user ID from security context
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            // Try to find user by username
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            User user = findByUsername(username);
            return user != null ? user.getId() : null;
        }
        return null;
    }

    /**
     * Create user
     */
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String email, String passwordHash, String role, BigDecimal balance) {
        // Check if email exists
        if (findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role != null ? role : "user");
        user.setBalance(balance != null ? balance : BigDecimal.ZERO);
        user.setConcurrency(5);
        user.setStatus("active");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if (!save(user)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create user");
        }

        log.info("Created user successfully: email={}, userId={}", email, user.getId());
        return user;
    }

    /**
     * Update user
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(User user) {
        User existing = findById(user.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);
    }

    /**
     * Update balance
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBalance(Long userId, BigDecimal amount) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        BigDecimal newBalance = user.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.USER_BALANCE_INSUFFICIENT);
        }

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setBalance(newBalance);
        updateUser.setUpdatedAt(OffsetDateTime.now());
        updateById(updateUser);

        log.info("Updated user balance: userId={}, amount={}, newBalance={}", userId, amount, newBalance);
    }

    /**
     * Update login info
     */
    public void updateLoginInfo(Long userId, String loginIp) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setUpdatedAt(OffsetDateTime.now());
        updateById(updateUser);
    }

    /**
     * Check user status
     */
    public void checkUserStatus(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
    }

    /**
     * Is admin
     */
    public boolean isAdmin(User user) {
        return "admin".equals(user.getRole());
    }

    /**
     * Enable TOTP
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableTotp(Long userId, String encryptedSecret) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setTotpSecretEncrypted(encryptedSecret);
        updateUser.setTotpEnabled(true);
        updateUser.setTotpEnabledAt(OffsetDateTime.now());
        updateUser.setUpdatedAt(OffsetDateTime.now());
        updateById(updateUser);
    }

    /**
     * Disable TOTP
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableTotp(Long userId) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setTotpSecretEncrypted(null);
        updateUser.setTotpEnabled(false);
        updateUser.setTotpEnabledAt(null);
        updateUser.setUpdatedAt(OffsetDateTime.now());
        updateById(updateUser);
    }

    /**
     * Soft delete user
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setDeletedAt(OffsetDateTime.now());
        updateUser.setUpdatedAt(OffsetDateTime.now());
        updateById(updateUser);
        log.info("Deleted user: userId={}", userId);
    }

    /**
     * Update user profile (username only for now)
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Long userId, String username) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Check if username is already taken by another user
        if (username != null && !username.isBlank() && !username.equals(user.getUsername())) {
            User existing = findByUsername(username);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
            user.setUsername(username);
        }

        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);
        log.info("Updated user profile: userId={}", userId);
        return user;
    }

    /**
     * Change user password
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify old password
        String oldHash = com.sub2api.module.common.util.EncryptionUtil.hashPassword(oldPassword, "");
        if (!oldHash.equals(user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        // Hash new password and update
        String newHash = com.sub2api.module.common.util.EncryptionUtil.hashPassword(newPassword, "");
        user.setPasswordHash(newHash);
        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);

        log.info("Changed password for user: userId={}", userId);
    }
}
