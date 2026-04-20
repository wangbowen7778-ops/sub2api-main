package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User admin controller
 *
 * @author Sub2API
 */
@Tag(name = "Admin - User", description = "User management API")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;
    private final ApiKeyService apiKeyService;

    @Operation(summary = "Paginated query user list")
    @GetMapping
    public Result<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) Boolean includeSubscriptions) {

        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .isNull(User::getDeletedAt)
                .orderByDesc(User::getCreatedAt);

        // Search in email and username
        if (search != null && !search.isBlank()) {
            String likePattern = "%" + search.trim() + "%";
            wrapper.and(w -> w.like(User::getEmail, likePattern)
                    .or().like(User::getUsername, likePattern));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq(User::getRole, role);
        }

        Page<User> result = userService.page(page, wrapper);

        // Filter by group name if specified (simple approach - fetch allowed groups)
        List<User> filteredUsers = result.getRecords();
        if (groupName != null && !groupName.isBlank()) {
            filteredUsers = filteredUsers.stream()
                    .filter(u -> {
                        List<Long> groups = userService.getAllowedGroups(u.getId());
                        // TODO: Check if any group name matches (would need to query groups table)
                        return !groups.isEmpty(); // Simplified for now
                    })
                    .collect(Collectors.toList());
        }

        // Clear sensitive info for each user
        for (User user : filteredUsers) {
            user.setPasswordHash(null);
            user.setTotpSecretEncrypted(null);
        }

        PageResult<User> pageResult = PageResult.of(result.getTotal(), filteredUsers, result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }

    @Operation(summary = "Get user details")
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }
        // Clear sensitive info
        user.setPasswordHash(null);
        user.setTotpSecretEncrypted(null);
        return Result.ok(user);
    }

    @Operation(summary = "Create user")
    @PostMapping
    public Result<User> createUser(@RequestBody CreateUserRequest request) {
        // Check if email already exists
        if (userService.findByEmail(request.getEmail()) != null) {
            return Result.fail(2033, "邮箱已被使用");
        }

        User user = userService.createUserFull(
                request.getEmail(),
                request.getPassword(),
                request.getUsername(),
                request.getNotes(),
                request.getBalance(),
                request.getConcurrency(),
                request.getAllowedGroups()
        );

        // Clear sensitive info
        user.setPasswordHash(null);
        user.setTotpSecretEncrypted(null);
        return Result.ok(user);
    }

    @Operation(summary = "Update user")
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }

        // Update fields
        if (request.getEmail() != null) {
            // Check if email already exists by another user
            User existing = userService.findByEmail(request.getEmail());
            if (existing != null && !existing.getId().equals(id)) {
                return Result.fail(2033, "邮箱已被使用");
            }
        }

        User updatedUser = userService.updateUserFull(
                id,
                request.getEmail(),
                request.getPassword(),
                request.getUsername(),
                request.getNotes(),
                request.getRole(),
                request.getBalance(),
                request.getConcurrency(),
                request.getStatus(),
                request.getAllowedGroups(),
                request.getGroupRates()
        );

        // Clear sensitive info
        updatedUser.setPasswordHash(null);
        updatedUser.setTotpSecretEncrypted(null);
        return Result.ok();
    }

    @Operation(summary = "Enable/disable user")
    @PatchMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam String status) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }
        user.setStatus(status);
        userService.updateById(user);
        return Result.ok();
    }

    @Operation(summary = "Adjust user balance (with operation type)")
    @PostMapping("/{id}/balance")
    public Result<Void> adjustBalance(@PathVariable Long id, @RequestBody BalanceAdjustRequest request) {
        // operation: "set", "add", "subtract"
        BigDecimal newBalance;
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }
        BigDecimal currentBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal amount = request.getBalance();

        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        switch (request.getOperation() != null ? request.getOperation().toLowerCase() : "set") {
            case "add":
                newBalance = currentBalance.add(amount);
                break;
            case "subtract":
                newBalance = currentBalance.subtract(amount);
                break;
            case "set":
            default:
                newBalance = amount;
                break;
        }

        userService.updateBalance(id, newBalance);
        return Result.ok();
    }

    @Operation(summary = "Get user's API keys")
    @GetMapping("/{id}/api-keys")
    public Result<List<Map<String, Object>>> getUserApiKeys(@PathVariable Long id) {
        // 返回用户的API密钥列表
        return Result.ok(List.of());
    }

    @Operation(summary = "Get user's usage statistics")
    @GetMapping("/{id}/usage")
    public Result<Map<String, Object>> getUserUsage(@PathVariable Long id) {
        return Result.ok(Map.of(
                "user_id", id,
                "total_requests", 0,
                "total_cost", BigDecimal.ZERO
        ));
    }

    @Operation(summary = "Get user's balance history")
    @GetMapping("/{id}/balance-history")
    public Result<List<Map<String, Object>>> getUserBalanceHistory(@PathVariable Long id) {
        return Result.ok(List.of());
    }

    @Operation(summary = "Replace user's exclusive group")
    @PostMapping("/{userId}/replace-group")
    public Result<Void> replaceUserGroup(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        return Result.ok();
    }

    @Operation(summary = "Delete user")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }
        // Cannot delete admin user
        if ("admin".equals(user.getRole())) {
            return Result.fail(1001, "Cannot delete admin user");
        }
        userService.deleteUser(id);
        return Result.ok();
    }

    // ==================== Request DTOs ====================

    @Data
    public static class BalanceAdjustRequest {
        private BigDecimal balance;
        private String operation; // "set", "add", "subtract"
        private String notes;
    }

    @Data
    public static class CreateUserRequest {
        private String email;
        private String password;
        private String username;
        private String notes;
        private BigDecimal balance;
        private Integer concurrency;
        private List<Long> allowedGroups;
    }

    @Data
    public static class UpdateUserRequest {
        private String email;
        private String password;
        private String username;
        private String notes;
        private String role;
        private BigDecimal balance;
        private Integer concurrency;
        private String status;
        private List<Long> allowedGroups;
        private Map<Long, Double> groupRates;
    }
}
