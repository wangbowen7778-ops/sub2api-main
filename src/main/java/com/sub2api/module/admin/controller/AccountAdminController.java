package com.sub2api.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.AccountStatus;
import com.sub2api.module.account.service.AccountHealthService;
import com.sub2api.module.account.service.AccountRefreshService;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account admin controller
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Admin - Account", description = "AI platform account management API")
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AccountAdminController {

    private final AccountService accountService;
    private final AccountRefreshService accountRefreshService;
    private final AccountHealthService accountHealthService;

    @Operation(summary = "Paginated query account list")
    @GetMapping
    public Result<PageResult<Account>> listAccounts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupId) {

        Page<Account> page = new Page<>(current, size);
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<Account>()
                .isNull(Account::getDeletedAt)
                .orderByDesc(Account::getCreatedAt);

        if (platform != null && !platform.isBlank()) {
            wrapper.eq(Account::getPlatform, platform);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Account::getStatus, status);
        }
        if (groupId != null) {
            // Filter by groupId needs association query, temporarily omitted
        }

        Page<Account> result = accountService.page(page, wrapper);
        PageResult<Account> pageResult = PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
        return Result.ok(pageResult);
    }

    @Operation(summary = "Get account details")
    @GetMapping("/{id}")
    public Result<Account> getAccount(@PathVariable Long id) {
        Account account = accountService.findById(id);
        if (account == null) {
            return Result.fail(4001, "Account not found");
        }
        return Result.ok(account);
    }

    @Operation(summary = "Create account")
    @PostMapping
    public Result<Account> createAccount(@RequestBody Account account) {
        Account created = accountService.createAccount(account);
        return Result.ok(created);
    }

    @Operation(summary = "Update account")
    @PutMapping("/{id}")
    public Result<Void> updateAccount(@PathVariable Long id, @RequestBody Account account) {
        account.setId(id);
        accountService.updateAccount(account);
        return Result.ok();
    }

    @Operation(summary = "Update account status")
    @PatchMapping("/{id}/status")
    public Result<Void> updateAccountStatus(@PathVariable Long id, @RequestParam String status) {
        accountService.updateStatus(id, AccountStatus.fromValue(status));
        return Result.ok();
    }

    @Operation(summary = "Reset token usage")
    @PatchMapping("/{id}/reset-usage")
    public Result<Void> resetUsage(@PathVariable Long id) {
        accountService.resetTokenUsage(id);
        return Result.ok();
    }

    @Operation(summary = "Refresh account credential")
    @PostMapping("/{id}/refresh")
    public Result<Void> refreshCredential(@PathVariable Long id) {
        accountRefreshService.manualRefresh(id);
        return Result.ok();
    }

    @Operation(summary = "Test account connectivity")
    @PostMapping("/{id}/test")
    public Result<Boolean> testConnection(@PathVariable Long id) {
        boolean result = accountHealthService.testAccountConnection(id);
        return Result.ok(result);
    }

    @Operation(summary = "Delete account")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return Result.ok();
    }

    @Operation(summary = "Get platform account list")
    @GetMapping("/platform/{platform}")
    public Result<List<Account>> listByPlatform(@PathVariable String platform) {
        List<Account> accounts = accountService.listByPlatform(platform);
        return Result.ok(accounts);
    }
}
