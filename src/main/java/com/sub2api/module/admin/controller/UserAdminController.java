package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * User admin controller
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - User", description = "User management API")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "Paginated query user list")
    @GetMapping
    public Result<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status) {

        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .isNull(User::getDeletedAt)
                .orderByDesc(User::getCreatedAt);

        if (username != null && !username.isBlank()) {
            wrapper.like(User::getUsername, username);
        }
        if (email != null && !email.isBlank()) {
            wrapper.eq(User::getEmail, email);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status);
        }

        Page<User> result = userService.page(page, wrapper);
        PageResult<User> pageResult = PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
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

    @Operation(summary = "Update user")
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(3001, "User not found");
        }

        // Update optional fields
        if (updates.containsKey("username")) {
            user.setUsername((String) updates.get("username"));
        }
        if (updates.containsKey("email")) {
            user.setEmail((String) updates.get("email"));
        }
        if (updates.containsKey("role")) {
            user.setRole((String) updates.get("role"));
        }
        if (updates.containsKey("status")) {
            user.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("concurrency")) {
            user.setConcurrency((Integer) updates.get("concurrency"));
        }
        if (updates.containsKey("balance")) {
            Object balance = updates.get("balance");
            if (balance instanceof Number) {
                user.setBalance(new java.math.BigDecimal(balance.toString()));
            }
        }
        if (updates.containsKey("notes")) {
            user.setNotes((String) updates.get("notes"));
        }

        userService.updateById(user);
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

    @Operation(summary = "Adjust user balance")
    @PatchMapping("/{id}/balance")
    public Result<Void> adjustBalance(@PathVariable Long id, @RequestParam Long amount) {
        userService.updateBalance(id, BigDecimal.valueOf(amount));
        return Result.ok();
    }

    @Operation(summary = "Delete user")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }
}
