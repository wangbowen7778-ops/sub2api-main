package com.sub2api.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sub2api.module.account.mapper.AccountGroupMapper;
import com.sub2api.module.account.mapper.AccountMapper;
import com.sub2api.module.account.mapper.GroupMapper;
import com.sub2api.module.account.mapper.ProxyMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.entity.Group;
import com.sub2api.module.account.model.entity.Proxy;
import com.sub2api.module.account.model.enums.AccountStatus;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.account.service.GroupService;
import com.sub2api.module.account.service.ProxyConfigService;
import com.sub2api.module.apikey.mapper.ApiKeyMapper;
import com.sub2api.module.apikey.model.entity.ApiKey;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.mapper.RedeemCodeMapper;
import com.sub2api.module.billing.model.entity.RedeemCode;
import com.sub2api.module.billing.service.RedeemCodeService;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.model.vo.PageResult;
import com.sub2api.module.user.mapper.UserMapper;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin Service
 * 管理员面板核心服务，处理用户、分组、账号、API Key、代理等管理操作
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final GroupService groupService;
    private final GroupMapper groupMapper;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final AccountGroupMapper accountGroupMapper;
    private final ApiKeyService apiKeyService;
    private final ApiKeyMapper apiKeyMapper;
    private final ProxyConfigService proxyConfigService;
    private final ProxyMapper proxyMapper;
    private final RedeemCodeService redeemCodeService;
    private final RedeemCodeMapper redeemCodeMapper;

    // ========== User Management ==========

    /**
     * 分页查询用户列表
     */
    public PageResult<User> listUsers(Long current, Long size, UserListFilters filters) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt);

        if (filters != null) {
            if (filters.getSearch() != null && !filters.getSearch().isBlank()) {
                wrapper.and(w -> w
                        .like(User::getEmail, filters.getSearch())
                        .or()
                        .like(User::getUsername, filters.getSearch()));
            }
            if (filters.getStatus() != null && !filters.getStatus().isBlank()) {
                wrapper.eq(User::getStatus, filters.getStatus());
            }
        }

        Page<User> result = userService.page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取用户详情
     */
    public User getUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 创建用户
     */
    @Transactional(rollbackFor = Exception.class)
    public User createUser(CreateUserInput input) {
        User existing = userService.getByEmail(input.getEmail());
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
        }

        User user = new User();
        user.setEmail(input.getEmail());
        user.setUsername(input.getUsername());
        user.setPassword(input.getPassword());
        user.setBalance(BigDecimal.valueOf(input.getBalance()));
        user.setConcurrency(input.getConcurrency());
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userService.save(user);
        log.info("Created user: id={}, email={}", user.getId(), user.getEmail());
        return user;
    }

    /**
     * 更新用户
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(Long userId, UpdateUserInput input) {
        User user = getUser(userId);

        if (input.getEmail() != null && !input.getEmail().isBlank()) {
            user.setEmail(input.getEmail());
        }
        if (input.getUsername() != null) {
            user.setUsername(input.getUsername());
        }
        if (input.getPassword() != null && !input.getPassword().isBlank()) {
            user.setPassword(input.getPassword());
        }
        if (input.getBalance() != null) {
            user.setBalance(BigDecimal.valueOf(input.getBalance()));
        }
        if (input.getConcurrency() != null) {
            user.setConcurrency(input.getConcurrency());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            user.setStatus(input.getStatus());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);

        log.info("Updated user: id={}", userId);
        return user;
    }

    /**
     * 删除用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        User user = getUser(userId);
        user.setDeletedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        log.info("Deleted user: id={}", userId);
    }

    /**
     * 更新用户余额
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateUserBalance(Long userId, double balance, String operation, String notes) {
        User user = getUser(userId);

        double currentBalance = user.getBalance() != null ? user.getBalance().doubleValue() : 0;
        double newBalance;

        if ("set".equals(operation)) {
            newBalance = balance;
        } else if ("add".equals(operation)) {
            newBalance = currentBalance + balance;
        } else if ("subtract".equals(operation)) {
            newBalance = currentBalance - balance;
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid operation");
        }

        if (newBalance < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Balance cannot be negative");
        }

        user.setBalance(BigDecimal.valueOf(newBalance));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);

        log.info("Updated user balance: userId={}, oldBalance={}, newBalance={}, operation={}",
                userId, currentBalance, newBalance, operation);
        return user;
    }

    // ========== Group Management ==========

    /**
     * 分页查询分组列表
     */
    public PageResult<Group> listGroups(Long current, Long size, String platform, String status, String search, Boolean isExclusive) {
        Page<Group> page = new Page<>(current, size);
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<Group>()
                .orderByAsc(Group::getSortOrder)
                .orderByDesc(Group::getCreatedAt);

        if (platform != null && !platform.isBlank()) {
            wrapper.eq(Group::getPlatform, platform);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Group::getStatus, status);
        }
        if (search != null && !search.isBlank()) {
            wrapper.like(Group::getName, search);
        }
        if (isExclusive != null) {
            wrapper.eq(Group::getIsExclusive, isExclusive);
        }

        Page<Group> result = groupService.page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取所有分组
     */
    public List<Group> getAllGroups() {
        return groupService.list();
    }

    /**
     * 按平台获取所有分组
     */
    public List<Group> getAllGroupsByPlatform(String platform) {
        return groupService.listByPlatform(platform);
    }

    /**
     * 获取分组详情
     */
    public Group getGroup(Long groupId) {
        Group group = groupService.getById(groupId);
        if (group == null || group.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Group not found");
        }
        return group;
    }

    /**
     * 创建分组
     */
    @Transactional(rollbackFor = Exception.class)
    public Group createGroup(CreateGroupInput input) {
        Group group = new Group();
        group.setName(input.getName());
        group.setDescription(input.getDescription());
        group.setPlatform(input.getPlatform());
        group.setRateMultiplier(input.getRateMultiplier());
        group.setIsExclusive(input.isExclusive());
        group.setSubscriptionType(input.getSubscriptionType() != null ? input.getSubscriptionType() : "standard");
        group.setStatus("active");
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());

        groupService.save(group);
        log.info("Created group: id={}, name={}", group.getId(), group.getName());
        return group;
    }

    /**
     * 更新分组
     */
    @Transactional(rollbackFor = Exception.class)
    public Group updateGroup(Long groupId, UpdateGroupInput input) {
        Group group = getGroup(groupId);

        if (input.getName() != null && !input.getName().isBlank()) {
            group.setName(input.getName());
        }
        if (input.getDescription() != null) {
            group.setDescription(input.getDescription());
        }
        if (input.getPlatform() != null && !input.getPlatform().isBlank()) {
            group.setPlatform(input.getPlatform());
        }
        if (input.getRateMultiplier() != null) {
            group.setRateMultiplier(input.getRateMultiplier());
        }
        if (input.getIsExclusive() != null) {
            group.setIsExclusive(input.getIsExclusive());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            group.setStatus(input.getStatus());
        }

        group.setUpdatedAt(LocalDateTime.now());
        groupService.updateById(group);

        log.info("Updated group: id={}", groupId);
        return group;
    }

    /**
     * 删除分组
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId) {
        Group group = getGroup(groupId);
        group.setDeletedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupService.updateById(group);
        log.info("Deleted group: id={}", groupId);
    }

    // ========== Account Management ==========

    /**
     * 分页查询账号列表
     */
    public PageResult<Account> listAccounts(Long current, Long size, String platform, String accountType, String status, String search, Long groupId) {
        Page<Account> page = new Page<>(current, size);
        LambdaQueryWrapper<Account> wrapper = new LambdaQueryWrapper<Account>()
                .isNull(Account::getDeletedAt)
                .orderByDesc(Account::getCreatedAt);

        if (platform != null && !platform.isBlank()) {
            wrapper.eq(Account::getPlatform, platform);
        }
        if (accountType != null && !accountType.isBlank()) {
            wrapper.eq(Account::getType, accountType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Account::getStatus, status);
        }
        if (search != null && !search.isBlank()) {
            wrapper.like(Account::getName, search);
        }

        Page<Account> result = accountService.page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取账号详情
     */
    public Account getAccount(Long accountId) {
        Account account = accountService.findById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /**
     * 创建账号
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(CreateAccountInput input) {
        Account account = new Account();
        account.setName(input.getName());
        account.setPlatform(input.getPlatform());
        account.setType(input.getType());
        account.setCredentials(input.getCredentials());
        account.setExtra(input.getExtra());
        account.setConcurrency(input.getConcurrency());
        account.setPriority(input.getPriority());
        if (input.getRateMultiplier() != null) {
            account.setRateMultiplier(input.getRateMultiplier());
        }
        account.setStatus("active");
        account.setSchedulable(true);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        accountService.save(account);

        if (input.getGroupIds() != null && !input.getGroupIds().isEmpty()) {
            for (Long groupId : input.getGroupIds()) {
                accountGroupMapper.insertAccountGroup(account.getId(), groupId);
            }
        }

        log.info("Created account: id={}, name={}, platform={}", account.getId(), account.getName(), account.getPlatform());
        return account;
    }

    /**
     * 更新账号
     */
    @Transactional(rollbackFor = Exception.class)
    public Account updateAccount(Long accountId, UpdateAccountInput input) {
        Account account = getAccount(accountId);

        if (input.getName() != null && !input.getName().isBlank()) {
            account.setName(input.getName());
        }
        if (input.getType() != null && !input.getType().isBlank()) {
            account.setType(input.getType());
        }
        if (input.getCredentials() != null) {
            account.setCredentials(input.getCredentials());
        }
        if (input.getExtra() != null) {
            account.setExtra(input.getExtra());
        }
        if (input.getConcurrency() != null) {
            account.setConcurrency(input.getConcurrency());
        }
        if (input.getPriority() != null) {
            account.setPriority(input.getPriority());
        }
        if (input.getRateMultiplier() != null) {
            account.setRateMultiplier(input.getRateMultiplier());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            account.setStatus(input.getStatus());
        }

        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);

        if (input.getGroupIds() != null) {
            accountGroupMapper.deleteByAccountId(accountId);
            if (!input.getGroupIds().isEmpty()) {
                for (Long groupId : input.getGroupIds()) {
                    accountGroupMapper.insertAccountGroup(accountId, groupId);
                }
            }
        }

        log.info("Updated account: id={}", accountId);
        return account;
    }

    /**
     * 删除账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        Account account = getAccount(accountId);
        account.setDeletedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        log.info("Deleted account: id={}", accountId);
    }

    /**
     * 刷新账号凭证
     */
    @Transactional(rollbackFor = Exception.class)
    public Account refreshAccountCredentials(Long accountId) {
        Account account = getAccount(accountId);
        account.setLastRefreshAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        return account;
    }

    /**
     * 清除账号错误状态
     */
    @Transactional(rollbackFor = Exception.class)
    public Account clearAccountError(Long accountId) {
        Account account = getAccount(accountId);
        account.setStatus(AccountStatus.ACTIVE.getValue());
        account.setLastError(null);
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        log.info("Cleared account error: id={}", accountId);
        return account;
    }

    /**
     * 设置账号错误状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void setAccountError(Long accountId, String errorMsg) {
        Account account = getAccount(accountId);
        account.setStatus(AccountStatus.ERROR.getValue());
        account.setLastError(errorMsg);
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        log.info("Set account error: id={}, error={}", accountId, errorMsg);
    }

    /**
     * 设置账号可调度状态
     */
    @Transactional(rollbackFor = Exception.class)
    public Account setAccountSchedulable(Long accountId, boolean schedulable) {
        Account account = getAccount(accountId);
        account.setSchedulable(schedulable);
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        log.info("Set account schedulable: id={}, schedulable={}", accountId, schedulable);
        return account;
    }

    // ========== Proxy Management ==========

    /**
     * 分页查询代理列表
     */
    public PageResult<Proxy> listProxies(Long current, Long size, String protocol, String status, String search) {
        Page<Proxy> page = new Page<>(current, size);
        LambdaQueryWrapper<Proxy> wrapper = new LambdaQueryWrapper<Proxy>()
                .isNull(Proxy::getDeletedAt)
                .orderByDesc(Proxy::getCreatedAt);

        if (protocol != null && !protocol.isBlank()) {
            wrapper.eq(Proxy::getProtocol, protocol);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Proxy::getStatus, status);
        }
        if (search != null && !search.isBlank()) {
            wrapper.like(Proxy::getName, search);
        }

        Page<Proxy> result = proxyConfigService.page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取所有代理
     */
    public List<Proxy> getAllProxies() {
        return proxyConfigService.listActive();
    }

    /**
     * 获取代理详情
     */
    public Proxy getProxy(Long proxyId) {
        Proxy proxy = proxyConfigService.findById(proxyId);
        if (proxy == null) {
            throw new BusinessException(ErrorCode.PROXY_NOT_FOUND);
        }
        return proxy;
    }

    /**
     * 创建代理
     */
    @Transactional(rollbackFor = Exception.class)
    public Proxy createProxy(CreateProxyInput input) {
        Proxy proxy = new Proxy();
        proxy.setName(input.getName());
        proxy.setProtocol(input.getProtocol());
        proxy.setHost(input.getHost());
        proxy.setPort(input.getPort());
        proxy.setUsername(input.getUsername());
        proxy.setPassword(input.getPassword());
        proxy.setStatus("active");
        proxy.setCreatedAt(LocalDateTime.now());
        proxy.setUpdatedAt(LocalDateTime.now());

        proxyConfigService.save(proxy);
        log.info("Created proxy: id={}, name={}", proxy.getId(), proxy.getName());
        return proxy;
    }

    /**
     * 更新代理
     */
    @Transactional(rollbackFor = Exception.class)
    public Proxy updateProxy(Long proxyId, UpdateProxyInput input) {
        Proxy proxy = getProxy(proxyId);

        if (input.getName() != null && !input.getName().isBlank()) {
            proxy.setName(input.getName());
        }
        if (input.getProtocol() != null && !input.getProtocol().isBlank()) {
            proxy.setProtocol(input.getProtocol());
        }
        if (input.getHost() != null && !input.getHost().isBlank()) {
            proxy.setHost(input.getHost());
        }
        if (input.getPort() != null) {
            proxy.setPort(input.getPort());
        }
        if (input.getUsername() != null) {
            proxy.setUsername(input.getUsername());
        }
        if (input.getPassword() != null) {
            proxy.setPassword(input.getPassword());
        }

        proxy.setUpdatedAt(LocalDateTime.now());
        proxyConfigService.updateById(proxy);

        log.info("Updated proxy: id={}", proxyId);
        return proxy;
    }

    /**
     * 删除代理
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProxy(Long proxyId) {
        proxyConfigService.deleteProxy(proxyId);
        log.info("Deleted proxy: id={}", proxyId);
    }

    // ========== Redeem Code Management ==========

    /**
     * 分页查询兑换码列表
     */
    public PageResult<RedeemCode> listRedeemCodes(Long current, Long size, String codeType, String status, String search) {
        Page<RedeemCode> page = new Page<>(current, size);
        LambdaQueryWrapper<RedeemCode> wrapper = new LambdaQueryWrapper<RedeemCode>()
                .orderByDesc(RedeemCode::getCreatedAt);

        if (codeType != null && !codeType.isBlank()) {
            wrapper.eq(RedeemCode::getCodeType, codeType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(RedeemCode::getStatus, status);
        }
        if (search != null && !search.isBlank()) {
            wrapper.like(RedeemCode::getCode, search);
        }

        Page<RedeemCode> result = redeemCodeService.page(page, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), result.getCurrent(), result.getSize());
    }

    /**
     * 获取兑换码详情
     */
    public RedeemCode getRedeemCode(Long redeemCodeId) {
        RedeemCode redeemCode = redeemCodeService.getById(redeemCodeId);
        if (redeemCode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Redeem code not found");
        }
        return redeemCode;
    }

    /**
     * 生成兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public List<RedeemCode> generateRedeemCodes(GenerateRedeemCodesInput input) {
        List<RedeemCode> codes = redeemCodeService.generateCodes(
                input.getCount(),
                input.getBalance(),
                input.getCodeType()
        );
        log.info("Generated {} redeem codes, type={}, balance={}", input.getCount(), input.getCodeType(), input.getBalance());
        return codes;
    }

    /**
     * 删除兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRedeemCode(Long redeemCodeId) {
        redeemCodeService.removeById(redeemCodeId);
        log.info("Deleted redeem code: id={}", redeemCodeId);
    }

    /**
     * 批量删除兑换码
     */
    @Transactional(rollbackFor = Exception.class)
    public long batchDeleteRedeemCodes(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            redeemCodeService.removeById(id);
            count++;
        }
        log.info("Batch deleted {} redeem codes", count);
        return count;
    }

    /**
     * 重置账号配额
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetAccountQuota(Long accountId) {
        Account account = getAccount(accountId);
        account.setUsedInputTokens(0);
        account.setUsedOutputTokens(0);
        account.setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        log.info("Reset account quota: id={}", accountId);
    }

    // ========== Input Classes ==========

    @Data
    public static class UserListFilters {
        private String search;
        private String status;
        private String role;
    }

    @Data
    public static class CreateUserInput {
        private String email;
        private String password;
        private String username;
        private String notes;
        private double balance;
        private int concurrency;
        private List<Long> allowedGroups;
    }

    @Data
    public static class UpdateUserInput {
        private String email;
        private String password;
        private String username;
        private String notes;
        private Double balance;
        private Integer concurrency;
        private String status;
        private List<Long> allowedGroups;
    }

    @Data
    public static class CreateGroupInput {
        private String name;
        private String description;
        private String platform;
        private double rateMultiplier;
        private boolean exclusive;
        private String subscriptionType;
    }

    @Data
    public static class UpdateGroupInput {
        private String name;
        private String description;
        private String platform;
        private Double rateMultiplier;
        private Boolean exclusive;
        private String status;
    }

    @Data
    public static class CreateAccountInput {
        private String name;
        private String platform;
        private String type;
        private java.util.Map<String, Object> credentials;
        private java.util.Map<String, Object> extra;
        private java.util.List<Long> groupIds;
        private int concurrency;
        private int priority;
        private Double rateMultiplier;
    }

    @Data
    public static class UpdateAccountInput {
        private String name;
        private String type;
        private java.util.Map<String, Object> credentials;
        private java.util.Map<String, Object> extra;
        private java.util.List<Long> groupIds;
        private Integer concurrency;
        private Integer priority;
        private Double rateMultiplier;
        private String status;
    }

    @Data
    public static class CreateProxyInput {
        private String name;
        private String protocol;
        private String host;
        private int port;
        private String username;
        private String password;
    }

    @Data
    public static class UpdateProxyInput {
        private String name;
        private String protocol;
        private String host;
        private Integer port;
        private String username;
        private String password;
    }

    @Data
    public static class GenerateRedeemCodesInput {
        private int count;
        private double balance;
        private String codeType;
    }
}
