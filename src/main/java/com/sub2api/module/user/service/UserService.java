package com.sub2api.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.user.mapper.UserAllowedGroupMapper;
import com.sub2api.module.user.mapper.UserGroupRateMultiplierMapper;
import com.sub2api.module.user.mapper.UserMapper;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.model.entity.UserAllowedGroup;
import com.sub2api.module.user.model.entity.UserGroupRateMultiplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User service
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final UserMapper userMapper;
    private final UserAllowedGroupMapper userAllowedGroupMapper;
    private final UserGroupRateMultiplierMapper userGroupRateMultiplierMapper;

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
        if (!com.sub2api.module.common.util.EncryptionUtil.verifyPassword(oldPassword, "", user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        // Hash new password and update
        String newHash = com.sub2api.module.common.util.EncryptionUtil.hashPassword(newPassword, "");
        user.setPasswordHash(newHash);
        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);

        log.info("Changed password for user: userId={}", userId);
    }

    /**
     * Get first admin user (for Admin API Key authentication)
     */
    public User getFirstAdmin() {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "admin")
                .isNull(User::getDeletedAt)
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
    }

    /**
     * Update user status
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, String status) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);
        log.info("Updated user status: userId={}, status={}", userId, status);
    }

    /**
     * Update user concurrency
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConcurrency(Long userId, Integer concurrency) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setConcurrency(concurrency);
        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);
        log.info("Updated user concurrency: userId={}, concurrency={}", userId, concurrency);
    }

    /**
     * Change password with TokenVersion increment (invalidates all existing tokens)
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordWithTokenVersion(Long userId, String oldPassword, String newPassword) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify old password
        if (!com.sub2api.module.common.util.EncryptionUtil.verifyPassword(oldPassword, "", user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }

        // Hash new password and update
        String newHash = com.sub2api.module.common.util.EncryptionUtil.hashPassword(newPassword, "");
        user.setPasswordHash(newHash);

        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);

        log.info("Changed password with token invalidation for user: userId={}", userId);
    }

    /**
     * Get user's allowed group IDs
     */
    public List<Long> getAllowedGroups(Long userId) {
        return userAllowedGroupMapper.selectGroupIdsByUserId(userId);
    }

    /**
     * Sync user's allowed groups (replace all)
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncAllowedGroups(Long userId, List<Long> groupIds) {
        // Delete existing associations
        userAllowedGroupMapper.deleteByUserId(userId);

        // Insert new associations
        if (groupIds != null && !groupIds.isEmpty()) {
            List<UserAllowedGroup> associations = new ArrayList<>();
            for (Long groupId : groupIds) {
                UserAllowedGroup association = new UserAllowedGroup();
                association.setUserId(userId);
                association.setGroupId(groupId);
                association.setCreatedAt(OffsetDateTime.now());
                associations.add(association);
            }
            for (UserAllowedGroup association : associations) {
                userAllowedGroupMapper.insert(association);
            }
        }

        log.info("Synced allowed groups for user: userId={}, groupIds={}", userId, groupIds);
    }

    /**
     * Get user's group rate multipliers
     * @return Map<groupId, rateMultiplier>
     */
    public Map<Long, Double> getGroupRates(Long userId) {
        List<Map<String, Object>> results = userGroupRateMultiplierMapper.selectRatesByUserId(userId);
        Map<Long, Double> rates = new HashMap<>();
        if (results != null) {
            for (Map<String, Object> row : results) {
                Long groupId = ((Number) row.get("group_id")).longValue();
                BigDecimal rate = (BigDecimal) row.get("rate_multiplier");
                rates.put(groupId, rate != null ? rate.doubleValue() : 1.0);
            }
        }
        return rates;
    }

    /**
     * Sync user's group rate multipliers (replace all)
     * null value in rates map means delete that rate
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncGroupRates(Long userId, Map<Long, Double> rates) {
        // Delete all existing rates first
        userGroupRateMultiplierMapper.deleteByUserId(userId);

        // Insert new rates
        if (rates != null && !rates.isEmpty()) {
            for (Map.Entry<Long, Double> entry : rates.entrySet()) {
                if (entry.getValue() != null) {
                    UserGroupRateMultiplier multiplier = new UserGroupRateMultiplier();
                    multiplier.setUserId(userId);
                    multiplier.setGroupId(entry.getKey());
                    multiplier.setRateMultiplier(BigDecimal.valueOf(entry.getValue()));
                    multiplier.setCreatedAt(OffsetDateTime.now());
                    multiplier.setUpdatedAt(OffsetDateTime.now());
                    userGroupRateMultiplierMapper.insert(multiplier);
                }
            }
        }

        log.info("Synced group rates for user: userId={}", userId);
    }

    /**
     * Create user with full fields
     */
    @Transactional(rollbackFor = Exception.class)
    public User createUserFull(String email, String password, String username, String notes,
                              BigDecimal balance, Integer concurrency, List<Long> allowedGroups) {
        // Check if email exists
        if (findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(com.sub2api.module.common.util.EncryptionUtil.hashPassword(password, ""));
        user.setUsername(username);
        user.setNotes(notes);
        user.setRole("user");
        user.setBalance(balance != null ? balance : BigDecimal.ZERO);
        user.setConcurrency(concurrency != null ? concurrency : 5);
        user.setStatus("active");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        if (!save(user)) {
            throw new BusinessException(ErrorCode.FAIL, "Failed to create user");
        }

        // Sync allowed groups
        if (allowedGroups != null && !allowedGroups.isEmpty()) {
            syncAllowedGroups(user.getId(), allowedGroups);
        }

        log.info("Created user full: email={}, userId={}", email, user.getId());
        return user;
    }

    /**
     * Update user with full fields (admin use)
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateUserFull(Long userId, String email, String password, String username, String notes,
                              String role, BigDecimal balance, Integer concurrency, String status,
                              List<Long> allowedGroups, Map<Long, Double> groupRates) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Admin cannot disable admin user
        if ("admin".equals(user.getRole()) && "disabled".equals(status)) {
            throw new BusinessException(ErrorCode.FAIL, "Cannot disable admin user");
        }

        // Update fields
        if (email != null) {
            // Check if email already exists by another user
            User existing = findByEmail(email);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
            user.setEmail(email);
        }
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(com.sub2api.module.common.util.EncryptionUtil.hashPassword(password, ""));
        }
        if (username != null) {
            user.setUsername(username);
        }
        if (notes != null) {
            user.setNotes(notes);
        }
        if (role != null) {
            user.setRole(role);
        }
        if (balance != null) {
            user.setBalance(balance);
        }
        if (concurrency != null) {
            user.setConcurrency(concurrency);
        }
        if (status != null) {
            user.setStatus(status);
        }

        user.setUpdatedAt(OffsetDateTime.now());
        updateById(user);

        // Sync allowed groups
        if (allowedGroups != null) {
            syncAllowedGroups(userId, allowedGroups);
        }

        // Sync group rates
        if (groupRates != null) {
            syncGroupRates(userId, groupRates);
        }

        log.info("Updated user full: userId={}", userId);
        return user;
    }
}
