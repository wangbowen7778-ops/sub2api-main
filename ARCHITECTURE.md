# Sub2API Java 后端架构设计

## 1. 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| **语言** | Java 21 | LTS 版本 |
| **框架** | Spring Boot 3.4 | 主流企业级框架 |
| **ORM** | MyBatis-Plus 3.5 | 轻量级、性能好，支持自动填充 |
| **依赖注入** | Spring DI | 原生支持 |
| **数据库** | PostgreSQL 15+ | 保持一致 |
| **缓存/队列** | Redis 7+ | Spring Data Redis |
| **API 文档** | SpringDoc OpenAPI 3 | Swagger UI |
| **构建工具** | Maven 3.9+ | 阿里巴巴推荐 |
| **代码规范** | 阿里巴巴Java开发规范 | 插件: Alibaba Code Guidelines |
| **部署方式** | Docker | docker-compose 部署 |
| **Web 框架** | Spring Web + WebSocket | 原生支持 SSE |
| **安全** | Spring Security + JWT | jjwt 库 |
| **日志** | SLF4J + Logback | 标准化 |
| **连接池** | HikariCP | Spring Boot 默认 |

---

## 2. 项目结构

```
backend-java/
├── pom.xml                              # Maven 配置
├── Dockerfile                           # Docker 镜像构建
├── docker-compose.yml                    # Docker Compose 部署
├── REFACTORING_TASKS.md                  # 重构任务清单
├── REFACTOR_RECORD.md                   # 重构记录
├── ARCHITECTURE.md                      # 架构文档
│
├── src/main/java/com/sub2api/
│   ├── Sub2ApiApplication.java           # 启动类
│   │
│   ├── config/                         # 配置层
│   │   ├── AppConfig.java              # 应用配置 (YAML映射)
│   │   ├── RedisConfig.java            # Redis 配置
│   │   ├── SecurityConfig.java         # Spring Security 配置
│   │   ├── WebSocketConfig.java        # WebSocket 配置
│   │   └── CorsConfig.java             # 跨域配置
│   │
│   ├── module/                          # 业务模块 (DDD分层)
│   │   ├── gateway/                     # API网关模块
│   │   │   ├── controller/
│   │   │   │   ├── GatewayController.java      # 代理转发入口
│   │   │   │   ├── ClaudeController.java        # Claude兼容API
│   │   │   │   ├── OpenAIController.java        # OpenAI兼容API
│   │   │   │   ├── GeminiController.java        # Gemini兼容API
│   │   │   │   └── AntigravityController.java   # Antigravity API
│   │   │   ├── service/
│   │   │   │   ├── ProxyService.java             # 代理转发服务
│   │   │   │   ├── ConcurrencyService.java       # 并发控制服务
│   │   │   │   ├── ProxyLatencyService.java      # 代理延迟追踪
│   │   │   │   ├── UsagePrefetchService.java     # 用量预取服务
│   │   │   │   ├── RpmCacheService.java          # RPM缓存服务
│   │   │   │   ├── SessionCacheService.java      # 会话缓存服务
│   │   │   │   ├── FailoverService.java          # 故障转移服务
│   │   │   │   ├── ClaudeCodeValidator.java        # Claude Code验证
│   │   │   │   ├── OpenAIGatewayService.java      # OpenAI网关服务
│   │   │   │   ├── GeminiMessagesCompatService.java # Gemini兼容性
│   │   │   │   ├── AntigravityService.java        # Antigravity服务
│   │   │   │   └── AntigravityQuotaService.java   # Antigravity配额
│   │   │   ├── websocket/
│   │   │   │   └── OpenAIWebSocketHandler.java   # WebSocket处理器
│   │   │   └── filter/
│   │   │       └── RequestBodyLimitFilter.java    # 请求体大小限制
│   │   │
│   │   ├── auth/                        # 认证模块
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java         # 登录/注册
│   │   │   │   ├── OAuthController.java        # OAuth跳转/回调
│   │   │   │   └── TokenController.java        # Token刷新
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java            # 认证核心逻辑
│   │   │   │   ├── OAuthService.java           # OAuth流程处理
│   │   │   │   ├── OpenAIOAuthService.java     # OpenAI OAuth服务
│   │   │   │   ├── JwtService.java             # JWT生成/验证
│   │   │   │   └── TOTPService.java            # 双因素认证
│   │   │   ├── platform/                       # OAuth 平台处理器
│   │   │   │   ├── OAuthHandler.java           # OAuth处理器接口
│   │   │   │   ├── AnthropicOAuthHandler.java
│   │   │   │   ├── OpenAIOAuthHandler.java
│   │   │   │   └── GoogleOAuthHandler.java
│   │   │   └── filter/
│   │   │       ├── JwtAuthenticationFilter.java
│   │   │       └── ApiKeyAuthenticationFilter.java
│   │   │
│   │   ├── admin/                       # 管理后台模块
│   │   │   ├── controller/
│   │   │   │   ├── UserAdminController.java
│   │   │   │   ├── AccountAdminController.java
│   │   │   │   ├── GroupAdminController.java
│   │   │   │   ├── APIKeyAdminController.java
│   │   │   │   ├── SubscriptionAdminController.java
│   │   │   │   ├── ProxyAdminController.java
│   │   │   │   ├── PromoCodeAdminController.java
│   │   │   │   ├── AnnouncementAdminController.java
│   │   │   │   ├── StatisticsController.java
│   │   │   │   ├── SettingAdminController.java
│   │   │   │   ├── ScheduledTestAdminController.java
│   │   │   │   └── ErrorPassthroughRuleController.java
│   │   │   ├── service/
│   │   │   │   ├── AdminService.java
│   │   │   │   └── ScheduledTestService.java
│   │   │   └── mapper/
│   │   │
│   │   ├── account/                      # 账号域 (核心)
│   │   │   ├── model/entity/
│   │   │   │   └── Account.java
│   │   │   ├── mapper/
│   │   │   │   ├── AccountMapper.java
│   │   │   │   └── AccountGroupMapper.java
│   │   │   └── service/
│   │   │       ├── AccountService.java
│   │   │       ├── AccountSelector.java
│   │   │       ├── AccountRefreshService.java
│   │   │       ├── AccountHealthService.java
│   │   │       ├── AccountExpiryService.java
│   │   │       ├── AccountTestService.java
│   │   │       ├── GroupService.java
│   │   │       ├── GroupCapacityService.java
│   │   │       ├── DeferredService.java
│   │   │       ├── IdentityService.java
│   │   │       └── RedisIdentityCache.java
│   │   │
│   │   ├── user/                         # 用户域
│   │   │   ├── controller/
│   │   │   │   └── AnnouncementController.java  # 用户公告API
│   │   │   ├── mapper/
│   │   │   │   └── AnnouncementReadMapper.java
│   │   │   ├── model/entity/
│   │   │   │   ├── User.java
│   │   │   │   ├── UserSubscription.java
│   │   │   │   └── AnnouncementRead.java
│   │   │   └── service/
│   │   │       ├── UserService.java
│   │   │       └── SubscriptionService.java
│   │   │
│   │   ├── billing/                      # 计费域
│   │   │   ├── model/entity/
│   │   │   │   ├── UsageLog.java
│   │   │   │   ├── PromoCode.java
│   │   │   │   └── RedeemCode.java
│   │   │   ├── mapper/
│   │   │   │   ├── UsageLogMapper.java
│   │   │   │   ├── PromoCodeMapper.java
│   │   │   │   └── RedeemCodeMapper.java
│   │   │   └── service/
│   │   │       ├── BillingService.java          # 综合计费服务
│   │   │       ├── BillingCalculator.java        # 计费计算器
│   │   │       ├── BillingCacheService.java      # 计费缓存服务
│   │   │       ├── RedisBillingCache.java        # Redis计费缓存
│   │   │       ├── PricingService.java           # 动态定价服务
│   │   │       ├── UsageLogService.java          # 用量日志服务
│   │   │       ├── PromoCodeService.java         # 兑换码服务
│   │   │       └── RateLimitService.java         # 限流服务
│   │   │
│   │   ├── apikey/                        # API Key域
│   │   │   ├── model/entity/
│   │   │   │   └── ApiKey.java
│   │   │   ├── mapper/
│   │   │   │   └── ApiKeyMapper.java
│   │   │   └── service/
│   │   │       ├── ApiKeyService.java
│   │   │       ├── ApiKeyCacheService.java
│   │   │       └── RedisApiKeyAuthCache.java
│   │   │
│   │   ├── channel/                      # 渠道域
│   │   │   ├── model/entity/
│   │   │   │   ├── Channel.java
│   │   │   │   ├── ChannelModelPricing.java
│   │   │   │   └── PricingInterval.java
│   │   │   ├── mapper/
│   │   │   │   ├── ChannelMapper.java
│   │   │   │   ├── ChannelModelPricingMapper.java
│   │   │   │   ├── PricingIntervalMapper.java
│   │   │   │   └── ChannelGroupMapper.java
│   │   │   └── service/
│   │   │       └── ChannelService.java
│   │   │
│   │   ├── dashboard/                    # 仪表盘域
│   │   │   ├── model/entity/
│   │   │   │   ├── UsageDashboardHourly.java       # 小时聚合实体
│   │   │   │   ├── UsageDashboardDaily.java        # 天聚合实体
│   │   │   │   ├── UsageDashboardHourlyUsers.java  # 小时活跃用户
│   │   │   │   ├── UsageDashboardDailyUsers.java   # 天活跃用户
│   │   │   │   └── DashboardAggregationWatermark.java # 聚合水位
│   │   │   ├── model/vo/
│   │   │   │   ├── DashboardStats.java
│   │   │   │   ├── TrendDataPoint.java
│   │   │   │   ├── ModelStat.java
│   │   │   │   ├── GroupStat.java
│   │   │   │   ├── GroupUsageSummary.java
│   │   │   │   ├── UserUsageTrendPoint.java
│   │   │   │   └── UserSpendingRankingResponse.java
│   │   │   ├── mapper/
│   │   │   │   ├── DashboardMapper.java
│   │   │   │   └── DashboardAggregationMapper.java # 预聚合 Mapper
│   │   │   ├── controller/
│   │   │   │   └── DashboardController.java
│   │   │   └── service/
│   │   │       ├── DashboardService.java
│   │   │       ├── DashboardAggregationService.java # 预聚合服务
│   │   │       └── DashboardAggregationConfig.java  # 预聚合配置
│   │   │
│   │   ├── ops/                           # 运维监控域
│   │   │   ├── model/entity/
│   │   │   │   └── OpsErrorLog.java
│   │   │   ├── model/vo/
│   │   │   │   └── OpsDashboardOverview.java
│   │   │   ├── mapper/
│   │   │   │   └── OpsErrorLogMapper.java
│   │   │   ├── controller/
│   │   │   │   └── OpsController.java
│   │   │   └── service/
│   │   │       ├── OpsService.java
│   │   │       ├── OpsAlertEvaluatorService.java
│   │   │       ├── OpsScheduledReportService.java  # 定时报表
│   │   │       └── SystemMetricsService.java
│   │   │
│   │   └── common/                       # 通用模块
│   │       ├── model/
│   │       │   ├── Result.java
│   │       │   ├── PageResult.java
│   │       │   └── ErrorCode.java
│   │       ├── exception/
│   │       │   ├── GlobalExceptionHandler.java
│   │       │   ├── BusinessException.java
│   │       │   └── RateLimitException.java
│   │       └── service/
│   │           ├── EmailService.java
│   │           ├── SettingService.java
│   │           ├── TurnstileService.java
│   │           └── GitHubReleaseService.java
│   │
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── mapper/
│           └── ChannelGroupMapper.xml
│
├── src/main/resources/
│   └── db/migration/
│
└── src/test/java/
    └── unit/
```

---

## 3. 核心模块设计

### 3.1 API 网关 (Gateway)

```
请求流程:
┌─────────┐    ┌──────────────┐    ┌─────────────────┐    ┌────────────┐
│  Client │───▶│ Gin Filter   │───▶│ DispatchService │───▶│  Platform  │
│         │◀───│ (认证/限流)   │◀───│   (账号调度)    │◀───│  Server    │
└─────────┘    └──────────────┘    └─────────────────┘    └────────────┘
                     │                      │
                     ▼                      ▼
              ┌─────────────┐        ┌────────────┐
              │ RateLimit   │        │ Billing    │
              │ Service      │        │ Service    │
              └─────────────┘        └────────────┘
```

**关键实现**:
- `DispatchService` - 根据分组/权重/状态选择最优账号
- `ProxyService` - 负责请求转发和响应处理
- `StreamHandler` - 处理 SSE 和 WebSocket 流式响应
- `RateLimitService` - Redis 滑动窗口限流

### 3.2 认证架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Authentication Flow                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐    ┌──────────────┐    ┌───────────────────┐  │
│  │  API Key │    │ JWT Cookie   │    │ OAuth (多平台)     │  │
│  └────┬─────┘    └──────┬───────┘    └─────────┬─────────┘  │
│       │                  │                      │             │
│       ▼                  ▼                      ▼             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              AuthStrategyFactory                        │ │
│  │  (根据请求头/参数选择对应认证策略)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
│                            │                                  │
│                            ▼                                  │
│                   ┌────────────────┐                         │
│                   │   AuthService  │                         │
│                   │  (核心认证逻辑) │                         │
│                   └────────────────┘                         │
│                            │                                  │
│                            ▼                                  │
│                   ┌────────────────┐                         │
│                   │   JwtService   │                         │
│                   └────────────────┘                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 数据库设计 (对应原 Ent Schema)

| 原 Ent Schema | Java 实体 | 类型 |
|---------------|-----------|------|
| account | Account | @TableName("account") |
| account_group | AccountGroup | 关联表 |
| user | User | @TableName("user") |
| api_key | ApiKey | @TableName("api_key") |
| group | Group | @TableName("app_group") |
| usage_log | UsageLog | @TableName("usage_log") |
| promo_code | PromoCode | @TableName("promo_code") |
| redeem_code | RedeemCode | @TableName("redeem_code") |
| proxy | Proxy | @TableName("proxy") |
| announcement | Announcement | @TableName("announcement") |
| setting | Setting | @TableName("setting") |
| scheduled_test_plan | ScheduledTestPlan | @TableName("scheduled_test_plans") |
| scheduled_test_result | ScheduledTestResult | @TableName("scheduled_test_results") |
| tls_fingerprint_profile | TLSFingerprintProfile | @TableName("tls_fingerprint_profiles") |
| channel | Channel | @TableName("channel") |
| channel_model_pricing | ChannelModelPricing | @TableName("channel_model_pricing") |
| pricing_interval | PricingInterval | @TableName("pricing_interval") |

---

## 4. 配置设计

```yaml
# application.yml 结构
sub2api:
  app:
    host: 0.0.0.0
    port: 8080
    debug: false

  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400  # 24小时
    refresh-expiration: 604800  # 7天

  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
    database: 0

  database:
    host: localhost
    port: 5432
    name: sub2api
    username: postgres
    password: ${DB_PASSWORD:}

  oauth:
    anthropic:
      client-id: ${ANTHROPIC_CLIENT_ID}
      client-secret: ${ANTHROPIC_CLIENT_SECRET}
    openai:
      client-id: ${OPENAI_CLIENT_ID}
      client-secret: ${OPENAI_CLIENT_SECRET}
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}

  gateway:
    default-timeout: 120
    max-retries: 3
    stream-timeout: 300
```

---

## 5. 与前端兼容性

- 前端 Vue 3 代码**无需修改**
- 保持相同的 API 路由结构
- 保持相同的请求/响应格式
- 支持现有 Docker 部署（只需替换后端镜像）

---

## 6. 开发计划

| 阶段 | 模块 | 工期预估 |
|------|------|----------|
| Phase 1 | 项目搭建、配置、通用模块 | 1-2 天 |
| Phase 2 | 用户认证模块 (注册/登录/OAuth/JWT) | 2-3 天 |
| Phase 3 | 管理后台 API (CRUD) | 3-4 天 |
| Phase 4 | API 网关核心 (代理/调度/限流) | 4-5 天 |
| Phase 5 | 计费系统 (用量追踪/计费) | 2-3 天 |
| Phase 6 | 联调测试 & Bug 修复 | 3-5 天 |
| **总计** | | **15-22 天** |

---

## 7. 需要确认

1. **技术栈是否合适？** (Spring Boot + MyBatis-Plus)
2. **数据库迁移方案？** (Flyway / MyBatis-Plus 自动迁移)
3. **是否需要保留所有 OAuth 平台？**
4. **是否有特定的 Java 编码规范？**
5. **部署方式偏好？** (Docker / Jar 直接部署)

---

## 8. 修改记录 (Changelog)

### 2026-04-10 - 修复编译错误和实现 TODO 功能

#### 修改内容

**1. Account 实体补充缺失字段**
- 新增字段：`refreshToken`(OAuth刷新令牌)、`credentialExpiredAt`(凭证过期时间)、`lastRefreshAt`(最后刷新时间)、`refreshErrorCount`(刷新错误计数)、`usedInputTokens`(已使用输入令牌)、`usedOutputTokens`(已使用输出令牌)、`inputTokenLimit`(输入令牌限制)、`outputTokenLimit`(输出令牌限制)、`lastError`(最后错误信息)

**2. UserAdminController 功能完善**
- `listUsers`: 实现带条件的分页查询（支持 username、email、status 过滤）
- `updateUser`: 实现用户更新逻辑（支持更新 username、email、role、status、concurrency、balance、notes）
- 修复敏感字段清除逻辑（passwordSalt → passwordHash, totpSecret → totpSecretEncrypted）
- 修复删除用户逻辑，使用软删除方法 `userService.deleteUser()`

**3. AccountAdminController 功能完善**
- `listAccounts`: 实现带条件的分页查询（支持 platform、status、groupId 过滤）

**4. StatisticsController 系统统计完善**
- `getOverview`: 实现系统总体统计功能
- 统计内容：总用户数(totalUsers)、总账号数(totalAccounts)、总请求数(totalRequests)、总费用(totalCost)、活跃API Keys数(activeApiKeys)

**5. AccountService 方法扩展**
- `listAllWithRefreshToken()`: 查询所有需要刷新的 OAuth 账号
- `listByPlatform()`: 根据平台查询账号列表
- `resetTokenUsage()`: 重置账号令牌使用量

**6. AccountRefreshService OAuth 刷新逻辑实现**
- `refreshAccountCredential()`: 实现 OAuth 凭证刷新逻辑
- 支持根据不同平台调用对应的刷新接口
- 更新凭证过期时间、刷新时间、错误计数

**7. AccountHealthService 连通性测试实现**
- `checkAccountHealth()`: 实现实际健康检查逻辑（调用平台 API）
- `testAccountConnectionInternal()`: 新增内部连通性测试方法
- `testAccountConnection()`: 实现实际的连通性测试（替换 placeholder）

#### 修改文件列表
- `src/main/java/com/sub2api/module/account/model/entity/Account.java`
- `src/main/java/com/sub2api/module/admin/controller/UserAdminController.java`
- `src/main/java/com/sub2api/module/admin/controller/AccountAdminController.java`
- `src/main/java/com/sub2api/module/admin/controller/StatisticsController.java`
- `src/main/java/com/sub2api/module/account/service/AccountService.java`
- `src/main/java/com/sub2api/module/account/service/AccountRefreshService.java`
- `src/main/java/com/sub2api/module/account/service/AccountHealthService.java`
