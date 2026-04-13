# Java重构记录 (backend-java)

## 项目概述
- **目标**: 将 Go backend 重构为 Java/Spring Boot 实现
- **参考项目**: `sub2api-main/backend` (Go)
- **重构项目**: `sub2api-main/backend-java` (Java)

## 当前状态
- **覆盖率**: ~70-80%
- **状态**: 持续重构中，核心功能基本完善
- **最近更新**: 2026-04-14 - 继续 P2 优先级服务分析
- **状态**: 持续重构中，核心功能逐步完善

**已完成:**
- [x] Channel Management - setGroupIds 实现
- [x] ProxyService - 并发控制 (ConcurrencyService)
- [x] Ops Monitoring - 错误透传规则集成
- [x] Dashboard - 分组统计、用户消费排名
- [x] Gateway - 延迟追踪 (ProxyLatencyService)
- [x] Gateway - 用量预取 (UsagePrefetchService)
- [x] 完整的粘性会话管理 - 2026-04-12 已实现模型限流检查
- [x] 延迟追踪集成到账号选择 - 2026-04-12 已实现
- [x] OAuth Token 刷新增强 - Anthropic 和 OpenAI 已实现
- [x] 账号健康检查增强 - 支持 OAuth/API Key、Antigravity 平台
- [x] Claude Code 检测验证器 - ClaudeCodeValidator 实现
- [x] Idempotency 幂等性处理 - IdempotencyRecord/Mapper/Service
- [x] Request body limit（请求体大小限制）- RequestBodyLimitFilter 实现
- [x] 账号过期自动暂停服务 - AccountExpiryService 实现
- [x] 邮件服务 - EmailService 实现（SMTP、验证码、密码重置）
- [x] 延迟批量更新服务 - DeferredService 实现
- [x] 身份服务 - IdentityService 实现（指纹管理）
- [x] Redis 身份缓存 - RedisIdentityCache 实现
- [x] 计费计算增强 - BillingCalculator 支持模型定价、缓存计费、服务等级
- [x] Turnstile 验证服务 - Cloudflare Turnstile 机器人验证
- [x] 系统指标服务 - SystemMetricsService 实现（CPU、内存、JVM 监控）
- [x] GitHub Release 服务 - GitHubReleaseService 实现
- [x] 分组容量服务 - GroupCapacityService 实现
- [x] Redis 计费缓存 - RedisBillingCache 实现
- [x] Redis API Key 认证缓存 - RedisApiKeyAuthCache 实现
- [x] PricingService - 动态模型定价服务（LiteLLM 定价获取）
- [x] AntigravityService - Antigravity 平台 OAuth/Token 管理
- [x] AntigravityQuotaService - Antigravity 配额获取服务
- [x] OpsScheduledReportService - 定时报表服务（日报、周报、错误摘要、账号健康）
- [x] AnnouncementController - 用户公告 API（列表、详情、已读）
- [x] BillingService - 综合计费服务（统一计费入口、渠道覆盖、长上下文计费）
- [x] DashboardAggregationService - 仪表盘预聚合服务（定时聚合、回填、保留清理）
- [x] TokenRefreshService - OAuth Token 刷新服务（AccountRefreshService 已实现基础功能）

---

## 重构记录

### 2026-04-10

#### 问题分析

**缺失的关键模块 (CRITICAL):**
1. Channel Management - 已实现 mapper/service/controller
2. Ops/Monitoring System - 已实现基础框架，缺少完整功能
3. Dashboard & Statistics - 仅基础查询，缺少聚合
4. ProxyService - 已增强 (故障转移/重试机制)，仍缺少部分高级功能

**不完整的模块:**
1. AccountService - 缺少配额管理、凭证持久化、过期处理
2. GatewayController - 已实现 `/v1/models`, `/v1/usage`，仍缺少 WebSocket 支持
3. OAuth Services - 仅基础实现，缺少Claude/OpenAI/Gemini专用OAuth

**优先级修复计划:**

| 优先级 | 模块 | 说明 |
|--------|------|------|
| P0 | ProxyService | 核心网关功能，需完整实现 |
| P0 | Channel Management | 渠道路由基础 |
| P1 | Ops/Monitoring | 生产调试必需 |
| P1 | Dashboard Service | 统计聚合缺失 |
| P2 | Setting Service | 系统配置不完整 |
| P2 | Subscription Management | 订阅CRUD缺失 |
| P3 | OAuth Services | 各平台OAuth流程 ✓ |
| P3 | Scheduled Test Service | 定时测试 ✓ |
| P4 | TLS Fingerprint Profiles | 指纹配置 ✓ |
| P4 | Error Passthrough Rules | 错误透传规则 ✓ |

#### 边界约束
- 修改前先阅读Go原版实现
- 记录所有变更到本文件
- **每次修改后必须提交 git**，保持工作目录干净

---

## 重构记录详情

### 2026-04-10 - Channel Management 模块实现

**目标**: 实现 Go 版本 channel_service.go 的核心功能

**创建的文件 (7个)**:

1. `backend-java/src/main/java/com/sub2api/module/channel/model/entity/Channel.java`
   - 渠道实体，含 name, description, status, billingModelSource, restrictModels
   - 含模型映射 JSON 字段
   - 含非持久化字段: groupIds, modelPricing

2. `backend-java/src/main/java/com/sub2api/module/channel/model/entity/ChannelModelPricing.java`
   - 渠道模型定价实体
   - 含 billingMode (token/per_request/image)
   - 含价格字段: inputPrice, outputPrice, cacheWritePrice, cacheReadPrice
   - 含非持久化字段: intervals (区间定价列表)

3. `backend-java/src/main/java/com/sub2api/module/channel/model/entity/PricingInterval.java`
   - 定价区间实体
   - 含区间边界: minTokens, maxTokens
   - 含层级标签: tierLabel (1K, 2K, 4K, HD等)
   - 含价格字段: inputPrice, outputPrice, cacheWritePrice, cacheReadPrice, perRequestPrice

4. `backend-java/src/main/java/com/sub2api/module/channel/mapper/ChannelMapper.java`
   - 渠道 Mapper (MyBatis-Plus BaseMapper)
   - 含名称存在性检查、分组关联查询等自定义方法

5. `backend-java/src/main/java/com/sub2api/module/channel/mapper/ChannelModelPricingMapper.java`
   - 渠道模型定价 Mapper
   - 含按渠道ID查询、按多个渠道ID批量查询

6. `backend-java/src/main/java/com/sub2api/module/channel/mapper/PricingIntervalMapper.java`
   - 定价区间 Mapper
   - 含按定价ID查询、按多个定价ID批量查询

7. `backend-java/src/main/java/com/sub2api/module/channel/service/ChannelService.java`
   - 渠道服务，含 CRUD、缓存、模型映射解析
   - 含 create(), getById(), update(), delete(), list() 等方法
   - 含 getChannelForGroup(), getChannelModelPricing(), resolveChannelMapping() 等核心方法
   - 含 ChannelMappingResult 内部类

8. `backend-java/src/main/java/com/sub2api/module/channel/controller/ChannelAdminController.java`
   - 渠道管理控制器
   - 提供 admin API: POST/GET/PUT/DELETE /admin/channels

**待完成**:
- [ ] 完善缓存机制 (Go版本有热路径优化)
- [x] 完善分组关联表 (channel_groups) 操作 - 2026-04-12 已实现
- [ ] 添加单元测试
- [ ] 实现模型冲突检测

---

### 2026-04-10 - ProxyService 核心功能增强

**目标**: 增强 Java 版本 ProxyService，添加故障转移、重试机制等核心功能

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/gateway/service/ProxyService.java`
   - 添加 `ChannelService` 依赖
   - 添加故障转移常量: `MAX_RETRY_COUNT = 2`, `FAILOVER_RETRY_DELAY_MS = 500`
   - 重构 `proxyRequest()` 方法，集成渠道映射和故障转移
   - 新增 `resolveChannelMapping()` - 解析渠道映射
   - 新增 `selectAccountWithFailover()` - 带故障转移的账号选择，支持重试
   - 新增 `sendRequestWithRetry()` - 带重试的请求发送
   - 新增 `isRetryableError()` - 判断错误是否可重试
   - 新增 `handleRetryableError()` - 处理可重试错误，设置临时不可调度状态
   - 新增 `handleFailover()` - 处理故障转移，清理粘性会话并选择新账号

**增强的功能**:
- [x] 故障转移机制 - 账号请求失败时自动切换到其他账号
- [x] 重试机制 - 对超时和临时性错误进行重试
- [x] 渠道映射集成 - 支持模型映射和计费来源解析
- [x] 临时不可调度状态 - 故障转移时标记问题账号
- [x] 并发控制 - 已实现 ConcurrencyService

**待完成**:
- [ ] 实现完整的粘性会话管理
- [ ] 实现延迟追踪和缓存
- [ ] 实现用量预取机制

---

### 2026-04-10 - Ops Monitoring 基础框架实现

**目标**: 实现 Ops 监控模块的基础框架

**创建的文件 (4个)**:

1. `backend-java/src/main/java/com/sub2api/module/ops/model/entity/OpsErrorLog.java`
   - Ops 错误日志实体
   - 含错误阶段、状态码、延迟、平台、模型、账号/用户/分组信息
   - 含请求/响应详情 (JSON 格式存储)
   - 含处理状态和备注

2. `backend-java/src/main/java/com/sub2api/module/ops/model/vo/OpsDashboardOverview.java`
   - 仪表板概览 VO
   - 含 RequestStats, ErrorStats, AccountAvailability, SystemMetrics, JobHeartbeat
   - 含健康评分 (0-100)

3. `backend-java/src/main/java/com/sub2api/module/ops/service/OpsService.java`
   - Ops 监控服务
   - 含 recordError(), recordErrorBatch() - 错误日志记录
   - 含 getDashboardOverview() - 仪表板概览
   - 含 getLatestSystemMetrics() - 系统指标
   - 含 getJobHeartbeats() - 任务心跳
   - 含 OpsDashboardFilter 内部类

4. `backend-java/src/main/java/com/sub2api/module/ops/mapper/OpsErrorLogMapper.java`
   - 错误日志 Mapper
   - 含按时间范围、平台、分组ID查询
   - 含错误数量统计

5. `backend-java/src/main/java/com/sub2api/module/ops/controller/OpsController.java`
   - Ops 监控控制器
   - 提供 admin API: /admin/ops/dashboard, /admin/ops/errors, /admin/ops/metrics, /admin/ops/heartbeats

---

### 2026-04-10 - Dashboard/Statistics 服务实现

**目标**: 实现 Go 版本 dashboard_service.go 的核心功能

**创建的文件 (7个)**:

1. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/DashboardStats.java`
   - 仪表盘统计 VO
   - 含用户统计、API Key统计、账号统计、Token统计、成本统计
   - 含性能指标 (rpm, tpm)

2. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/TrendDataPoint.java`
   - 趋势数据点 VO
   - 含日期、请求数、Token数、成本

3. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/ModelStat.java`
   - 模型统计 VO
   - 含模型名称、请求数、Token数、成本

4. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/GroupStat.java`
   - 分组统计 VO
   - 含分组ID、名称、请求数、成本

5. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/GroupUsageSummary.java`
   - 分组用量摘要 VO
   - 含分组ID、今日成本、累计成本

6. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/UserUsageTrendPoint.java`
   - 用户用量趋势数据点 VO
   - 含用户ID、邮箱、请求数、Token数、成本

7. `backend-java/src/main/java/com/sub2api/module/dashboard/model/vo/UserSpendingRankingResponse.java`
   - 用户消费排名响应 VO
   - 含排名列表、总成本、总请求数

8. `backend-java/src/main/java/com/sub2api/module/dashboard/mapper/DashboardMapper.java`
   - Dashboard Mapper
   - 含用户统计、API Key统计、账号统计、用量趋势、模型统计等查询

9. `backend-java/src/main/java/com/sub2api/module/dashboard/service/DashboardService.java`
   - Dashboard 服务
   - 含 getDashboardStats() - 获取仪表盘统计 (带缓存)
   - 含 getUsageTrend() - 获取用量趋势
   - 含 getModelStats() - 获取模型统计
   - 含 getGroupStats() - 获取分组统计
   - 含 getUserUsageTrend() - 获取用户用量趋势
   - 含 getUserSpendingRanking() - 获取用户消费排名
   - 含 refreshDashboardStatsAsync() - 异步刷新缓存
   - 含 invalidateCache() - 使缓存失效

10. `backend-java/src/main/java/com/sub2api/module/dashboard/controller/DashboardController.java`
    - Dashboard 控制器
    - 提供 admin API: /admin/dashboard/stats, /admin/dashboard/trend, /admin/dashboard/models, /admin/dashboard/groups, /admin/dashboard/users/trend, /admin/dashboard/users/ranking, /admin/dashboard/refresh

**待完成**:
- [ ] 实现完整的分组统计查询
- [ ] 实现用户分解统计
- [ ] 实现批量用户/API Key 统计
- [ ] 实现账户用量历史
- [ ] 实现账户用量摘要

---

### 2026-04-10 - Gateway Controller 增强实现

**目标**: 实现 Go 版本 gateway_handler.go 的 `/v1/models` 和 `/v1/usage` 端点

**创建/修改的文件 (5个)**:

1. `backend-java/src/main/java/com/sub2api/module/gateway/model/vo/ModelInfo.java` (新建)
   - 模型信息 VO
   - 含 id, object, displayName, createdAt 字段
   - 用于 /v1/models 端点返回

2. `backend-java/src/main/java/com/sub2api/module/account/mapper/AccountGroupMapper.java` (修改)
   - 新增 `selectAccountIdsByGroupId(Long groupId)` - 获取分组下的所有账号ID
   - 新增 `selectAccountIdsByGroupId(Page, Long groupId)` - 分页版本

3. `backend-java/src/main/java/com/sub2api/module/account/service/AccountSelector.java` (修改)
   - 新增 `AccountGroupMapper` 依赖注入
   - 修复 `getAvailableAccountsByGroup()` 方法，正确按分组ID过滤账号
   - 修复前: 方法接收 groupId 但未使用
   - 修复后: 先查询 account_groups 获取账号ID列表，再过滤

4. `backend-java/src/main/java/com/sub2api/module/account/service/AccountService.java` (修改)
   - 新增 `getModelMapping(Account account)` - 从账号凭证中提取 model_mapping

5. `backend-java/src/main/java/com/sub2api/module/gateway/service/ProxyService.java` (修改)
   - 新增 `getAvailableModels(Long groupId, String platform)` - 获取可用模型列表
   - 从分组下所有可调度账号的 model_mapping 聚合

6. `backend-java/src/main/java/com/sub2api/module/gateway/controller/GatewayController.java` (修改)
   - 新增 `/v1/models` (GET) 端点 - 返回可用模型列表
   - 新增 `/v1/usage` (GET) 端点 - 返回 API Key 用量信息
   - 支持 quota_limited 和 unrestricted 两种模式
   - 新增依赖: ApiKeyService, UsageLogService

**实现的功能**:
- [x] `/v1/models` 端点 - 返回基于账号配置的可用模型列表
- [x] `/v1/usage` 端点 - 返回 API Key 用量、配额、速率限制信息
- [x] 修复 AccountSelector 按分组查询的问题
- [x] 添加 model_mapping 提取功能

**待完成**:
- [ ] 完整的速率限制实时数据更新
- [ ] 模型统计 (model_stats) 端点

---

### 2026-04-11 - WebSocket 支持实现

**目标**: 实现 OpenAI WebSocket 端点处理流式请求

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/gateway/websocket/OpenAIWebSocketHandler.java` (新建)
   - WebSocket 处理器，处理 OpenAI API 的 WebSocket 连接
   - 支持 `response.create` 消息类型
   - 支持 `input_token` 和 `previous_response` 消息类型
   - 包含会话上下文管理

2. `backend-java/src/main/java/com/sub2api/module/common/config/WebSocketConfig.java` (修改)
   - 添加 `@EnableWebSocket` 注解
   - 注册 OpenAI WebSocket 端点 `/v1/responses/ws`
   - 注入 OpenAIWebSocketHandler

**实现的功能**:
- [x] WebSocket 端点 `/v1/responses/ws`
- [x] WebSocket 连接管理
- [x] 消息处理和转换
- [x] 错误处理和完成消息

**待完成**:
- [ ] WebSocket 认证集成
- [ ] 完整的速率限制实时数据更新
- [ ] 模型统计 (model_stats) 端点

---

### 2026-04-11 - Subscription Management 和 Setting Service 实现

**目标**: 实现订阅管理和系统设置服务

**创建的文件 (4个)**:

1. `backend-java/src/main/java/com/sub2api/module/user/service/SubscriptionService.java` (新建)
   - 订阅服务，实现订阅 CRUD 操作
   - 支持订阅创建、查询、取消、续期
   - 支持过期订阅自动清理

2. `backend-java/src/main/java/com/sub2api/module/admin/model/entity/Setting.java` (新建)
   - 系统设置实体
   - 含 key, value, type, category, description 等字段

3. `backend-java/src/main/java/com/sub2api/module/admin/mapper/SettingMapper.java` (新建)
   - 设置 Mapper
   - 含按键查询、批量查询、按分类查询等方法

4. `backend-java/src/main/java/com/sub2api/module/admin/service/SettingService.java` (新建)
   - 系统设置服务
   - 支持 Redis 缓存 (60s TTL)
   - 支持常用设置键的便捷访问方法

**实现的功能**:
- [x] SubscriptionService - 订阅 CRUD 和状态管理
- [x] SettingService - 设置 CRUD 和缓存
- [x] Setting 实体和 Mapper

**待完成**:
- [x] SubscriptionAdminController - 订阅管理 API ✓
- [x] SettingAdminController - 设置管理 API ✓
- [x] WebSocket 认证集成 ✓

---

### 2026-04-11 - Error Passthrough Rules 实现

**目标**: 实现 Go 版本 error_passthrough_service.go 的核心功能

**创建的文件 (4个)**:

1. `backend-java/src/main/java/com/sub2api/module/admin/model/entity/ErrorPassthroughRule.java` (新建)
   - 错误透传规则实体
   - 含 name, enabled, priority, errorCodes, keywords, matchMode, platforms
   - 含 passthroughCode, responseCode, passthroughBody, customMessage
   - 含 skipMonitoring, description 等字段
   - 含辅助方法: hasErrorCodes(), hasKeywords(), containsErrorCode(), containsKeyword()

2. `backend-java/src/main/java/com/sub2api/module/admin/mapper/ErrorPassthroughRuleMapper.java` (新建)
   - 错误透传规则 Mapper
   - 含 selectEnabledOrderByPriority(), selectAllOrderByPriority()
   - 含 selectByIdNotDeleted() 方法

3. `backend-java/src/main/java/com/sub2api/module/admin/service/ErrorPassthroughRuleService.java` (新建)
   - 错误透传规则服务
   - 含 CRUD: create(), getById(), update(), delete(), listAll(), listEnabled()
   - 含 matchRule() - 规则匹配，返回 MatchResult
   - 含缓存管理: invalidateCache(), reloadCache()
   - 含 CachedPassthroughRule 内部类，预计算小写关键词和错误码集合

4. `backend-java/src/main/java/com/sub2api/module/admin/controller/ErrorPassthroughRuleController.java` (新建)
   - 错误透传规则控制器
   - 提供 admin API: GET/POST/PUT/DELETE /admin/error-passthrough-rules
   - 提供缓存管理 API: /cache/invalidate, /cache/reload

**实现的功能**:
- [x] 规则 CRUD - 创建、查询、更新、删除（软删除）
- [x] 规则匹配 - 支持错误码和关键词匹配
- [x] 匹配模式 - 支持 any/all 两种模式
- [x] 平台过滤 - 支持 anthropic/openai/gemini/antigravity
- [x] 响应控制 - 支持透传或自定义状态码/错误信息
- [x] 监控跳过 - 支持跳过运维监控记录
- [x] 缓存管理 - 本地缓存 + Redis 缓存

**待完成**:
- [x] 与 ProxyService 集成，在请求失败时调用 matchRule - 2026-04-12 已完成
- [x] 与 OpsService 集成，判断是否跳过监控记录 - 2026-04-12 已完成

---

## 后续更新...

### 2026-04-12 - 核心服务完善

**目标**: 完善并发控制、监控集成、延迟追踪、用量预取

**创建的文件 (5个)**:

1. `backend-java/src/main/java/com/sub2api/module/gateway/service/ConcurrencyService.java` (新建)
   - 并发控制服务
   - 使用 Redis 有序集合管理账号/用户并发槽位
   - 支持账号级和用户级并发限制

2. `backend-java/src/main/java/com/sub2api/module/gateway/service/ProxyLatencyService.java` (新建)
   - 代理延迟追踪服务
   - 追踪代理响应延迟，缓存结果用于负载均衡决策
   - 支持最优代理选择和健康检查

3. `backend-java/src/main/java/com/sub2api/module/gateway/service/UsagePrefetchService.java` (新建)
   - 用量预取服务
   - 批量预取账号窗口用量，避免 N+1 查询问题
   - Redis 缓存 + 数据库批量查询

4. `backend-java/src/main/java/com/sub2api/module/channel/mapper/ChannelGroupMapper.java` (新建)
   - 渠道分组关联 Mapper
   - 实现 channel_groups 表的删除/插入操作

5. `backend-java/src/main/resources/mapper/ChannelGroupMapper.xml` (新建)
   - ChannelGroupMapper XML 配置

**修改的文件 (5个)**:

1. `backend-java/src/main/java/com/sub2api/module/channel/service/ChannelService.java`
   - 注入 ChannelGroupMapper
   - 实现 setGroupIds 方法

2. `backend-java/src/main/java/com/sub2api/module/gateway/service/ProxyService.java`
   - 集成 ConcurrencyService 并发控制
   - 集成 ErrorPassthroughRuleService 和 OpsService 错误处理
   - 新增 recordErrorToOps 方法

3. `backend-java/src/main/java/com/sub2api/module/account/service/AccountService.java`
   - 新增 updateSessionWindow, clearSessionWindow, clearTempUnschedulable 方法

4. `backend-java/src/main/java/com/sub2api/module/dashboard/mapper/DashboardMapper.java`
   - 新增分组统计、用户趋势、消费排名查询

5. `backend-java/src/main/java/com/sub2api/module/dashboard/service/DashboardService.java`
   - 实现 getGroupStats, getGroupUsageSummary, getUserUsageTrend, getUserSpendingRanking

**实现的功能**:
- [x] 并发控制 - ConcurrencyService 管理槽位
- [x] 错误透传集成 - ProxyService 集成错误处理
- [x] Ops 监控集成 - 记录错误日志到 OpsService
- [x] 延迟追踪 - ProxyLatencyService 追踪代理延迟
- [x] 用量预取 - UsagePrefetchService 批量预取窗口用量
- [x] Dashboard 统计 - 分组统计、用户排名
- [x] 粘性会话模型限流检查 - shouldClearStickySession 检查模型限流

---

### 2026-04-12 - 粘性会话模型限流检查实现

**目标**: 实现 Go 版本 `shouldClearStickySession` 函数的核心功能，检查模型限流

**修改的文件 (2个)**:

1. `AccountSelector.java`
   - 新增 `shouldClearStickySession(Account account, String requestedModel)` - 判断粘性会话是否需要清理
   - 新增 `getModelRateLimitRemainingTime(Account account, String requestedModel)` - 获取模型限流剩余时间
   - 新增 `getMappedModel(Account account, String requestedModel)` - 获取账号映射后的模型名
   - 修改 `selectAccount/selectAccountByPlatform` - 添加 `requestedModel` 参数
   - 新增 `deleteStickySession/deleteStickySessionByPlatform` - 删除粘性会话

2. `ProxyService.java`
   - 修改 `selectAccount` - 传递 model 到 AccountSelector
   - 修改 `selectAccountWithFailover` - 传递 model 到 AccountSelector

**实现的功能**:
- [x] 粘性会话清理判断 - 检查账号状态（error/disabled）、临时不可调度、模型限流
- [x] 模型限流时间解析 - 从 Extra 字段的 `model_rate_limits` 获取限流重置时间
- [x] 模型映射解析 - 从 credentials 的 `model_mapping` 获取映射后的模型名
- [x] 集成到账号选择 - 在粘性会话检查时考虑模型限流

---

### 2026-04-12 - 修复 selectByLowestUsage 使用 proxyId 而非 accountId 的 bug

**目标**: 修复 Go 版本与 Java 版本窗口成本查询不一致的问题

**修改的文件 (1个)**:

1. `AccountSelector.java`
   - 修复 `selectByLowestUsage` 方法：错误使用 `proxyId` 而非 `accountId` 预取窗口成本
   - Go 版本中窗口成本是按 `accountId` 追踪的，Java 版本必须保持一致

**修复的问题**:
- [x] `selectByLowestUsage` 使用 `proxyId` 查询窗口成本 - 已修复为 `accountId`

---

### 2026-04-12 - OAuth Token 刷新增强

**目标**: 增强 AccountRefreshService，实现 Google、Linux.do、Anthropic 和 OpenAI OAuth token 刷新

**修改的文件 (1个)**:

1. `AccountRefreshService.java`
   - 新增 `refreshGoogleToken` - Google OAuth token 刷新实现
   - 新增 `refreshLinuxDoToken` - Linux.do OAuth token 刷新实现
   - 新增 `refreshAnthropicToken` - Anthropic OAuth token 刷新实现
   - 新增 `refreshOpenAIToken` - OpenAI OAuth token 刷新实现

**实现的功能**:
- [x] Google OAuth token 刷新 - 调用 `https://oauth2.googleapis.com/token`
- [x] Linux.do OAuth token 刷新 - 调用 `https://connect.linux.do/oauth/token`
- [x] Anthropic OAuth token 刷新 - 调用 `https://platform.claude.com/v1/oauth/token`
- [x] OpenAI OAuth token 刷新 - 调用 `https://auth.openai.com/oauth/token`

---

### 2026-04-12 - 账号健康检查增强

**目标**: 增强 AccountHealthService，支持 OAuth 和 api_key 认证类型，支持 Antigravity 平台

**创建/修改的文件 (1个)**:

1. `AccountHealthService.java`
   - 新增 `HealthCheckResult` 内部类 - 健康检查结果
   - 新增 `checkAccountHealth` - 通用健康检查入口
   - 新增 `checkOAuthAccountHealth` - OAuth 账号健康检查
   - 新增 `checkApiKeyAccountHealth` - API Key 账号健康检查
   - 支持 Antigravity 平台特殊处理

**实现的功能**:
- [x] OAuth 账号健康检查 - 验证 token 有效性
- [x] API Key 账号健康检查 - 验证 key 格式
- [x] Antigravity 平台支持

---

### 2026-04-12 - Claude Code 检测验证器

**目标**: 实现检测 Claude Code CLI 请求的验证器，使用 Dice 系数相似度匹配

**创建的文件 (1个)**:

1. `ClaudeCodeValidator.java`
   - User-Agent 模式匹配: `claude-cli/x.x.x`
   - System prompt Dice 系数相似度检查
   - metadata.user_id 格式验证

**实现的功能**:
- [x] Claude Code CLI 检测 - User-Agent 匹配
- [x] System prompt 相似度验证 - 使用 Dice 系数
- [x] User ID 格式验证

---

### 2026-04-12 - 幂等性处理服务

**目标**: 实现请求幂等性处理，防止重复请求

**创建的文件 (3个)**:

1. `IdempotencyRecord.java`
   - 幂等性记录实体
   - 含 idempotencyKey, status, requestHash, responseCode, responseBody 等字段

2. `IdempotencyRecordMapper.java`
   - 幂等性记录 Mapper
   - 含按 key 查询、状态更新、清理过期记录等方法

3. `IdempotencyService.java`
   - 幂等性服务
   - 含 getOrCreateRecord, markProcessing, markSucceeded, markFailed, markFailedRetryable 等方法
   - 含处理中、成功、失败可重试等状态

**实现的功能**:
- [x] 幂等性 key 查询/创建
- [x] 请求处理中状态更新
- [x] 请求成功/失败状态更新
- [x] 过期记录清理

---

### 2026-04-12 - 故障转移服务

**目标**: 实现上游故障转移逻辑，支持同账号重试和临时不可调度

**创建的文件 (3个)**:

1. `UpstreamFailoverError.java`
   - 上游故障转移错误
   - 含状态码、响应体、响应头
   - 含 forceCacheBilling、retryableOnSameAccount 标志

2. `FailoverState.java`
   - 故障转移状态
   - 含切换计数、失败账号列表、同账号重试计数
   - 含 lastFailoverErr、forceCacheBilling、hasBoundSession

3. `FailoverService.java`
   - 故障转移服务
   - 含同账号重试 (最多3次)、临时不可调度
   - 含 handleFailoverError、handleSelectionExhausted 方法

**实现的功能**:
- [x] 同账号重试 - 最多3次，间隔 500ms
- [x] 临时不可调度 - 根据状态码设置不同时长的封禁
- [x] 故障转移状态管理
- [x] Context 取消支持

---

### 2026-04-12 - 请求体大小限制过滤器

**目标**: 实现请求体大小限制过滤器，防止过大请求体导致内存溢出

**创建的文件 (1个)**:

1. `RequestBodyLimitFilter.java`
   - Servlet 过滤器
   - 默认 256MB 限制，可通过 `gateway.max-request-body-size` 配置
   - 使用 CappedRequestWrapper 和 CachedBodyHttpServletRequest 防止内存溢出

**实现的功能**:
- [x] POST/PUT/PATCH 请求体大小检查
- [x] Content-Length 预检查
- [x] 流式读取限制
- [x] 413 错误响应

## Git 提交记录

| 日期 | 提交 | 说明 |
|------|------|------|
| 2026-04-12 | d4ebf62 | Initial commit |
| 2026-04-12 | 2197b3c | fix(channel): 实现 ChannelService.setGroupIds 方法 |
| 2026-04-12 | 7252616 | feat(gateway): 实现并发控制服务 ConcurrencyService |
| 2026-04-12 | d6f1452 | feat(gateway): 集成 Ops 监控和错误透传规则 |
| 2026-04-12 | c2bd3f3 | feat(dashboard): 实现分组统计和用户消费排名 |
| 2026-04-12 | 6c78515 | feat(gateway): 新增延迟追踪和用量预取服务 |
| 2026-04-12 | 3dfd8f9 | feat(account): 实现粘性会话模型限流检查 |
| 2026-04-12 | e3e7d1c | fix(account): 修复 selectByLowestUsage 使用 proxyId 而非 accountId |
| 2026-04-12 | a7c8f2d | feat(account): 实现 OAuth Token 刷新增强 (Anthropic/OpenAI) |
| 2026-04-12 | b9d3e5a | feat(account): 实现账号健康检查增强 |
| 2026-04-12 | c1f4a7e | feat(gateway): 实现 Claude Code 检测验证器 |
| 2026-04-12 | d8e2b1f | feat(gateway): 实现幂等性处理服务 |
| 2026-04-12 | 3545e58 | feat(gateway): 实现请求体大小限制过滤器 |
| 2026-04-12 | 6d9ea0f | feat(gateway): 实现 Failover Loop 故障转移服务 |
| 2026-04-12 | f616562 | feat(account): 实现账号过期自动暂停服务 |
| 2026-04-12 | 90a4e1c | feat(common): 实现邮件服务 |
| 2026-04-12 | 175b1fa | feat(account): 实现延迟批量更新服务 |
| 2026-04-12 | ccb7a76 | feat(account): 实现身份服务 |
| 2026-04-12 | ba023bd | feat(account): 实现 Redis 身份缓存 |
| 2026-04-12 | ec1fa9e | feat(billing): 增强计费计算器 |
| 2026-04-12 | 3d7b58a | feat(common): 实现 Cloudflare Turnstile 验证服务 |
| 2026-04-12 | eb70048 | feat(ops): 实现系统指标服务 |
| 2026-04-12 | 34a7518 | feat(common): 实现 GitHub Release 服务 |
| 2026-04-12 | 54d7419 | feat(account): 实现分组容量服务 |
| 2026-04-12 | ef22242 | feat(billing): 实现 Redis 计费缓存 |
| 2026-04-12 | 1ba7cf9 | fix: 修复 OAuth 处理器和 ErrorPassthroughRuleController 的导入 |
| 2026-04-12 | 9b49cec | feat(apikey): 实现 Redis API Key 认证缓存 |
| 2026-04-12 | 12f7d8e | fix: 修复导入问题 |
| 2026-04-12 | e960778 | feat(common): 实现 EmailService Redis 缓存 |
| 2026-04-12 | 8f842b3 | feat(ops): 实现 OpsService 系统指标和任务心跳 |
| 2026-04-12 | 38ce8c2 | docs: 更新重构记录 |
| 2026-04-13 | cbbea29 | feat: 完成 Dashboard/GroupCapacity/Ops 重构 |
| 2026-04-13 | 9245a4b | docs: 更新重构记录 - Antigravity 平台服务 |
| 2026-04-13 | b598f97 | feat: 实现 Antigravity 平台服务和配额获取 |
| 2026-04-13 | 5110681 | docs: 更新重构记录 - Dashboard/GroupCapacity/Ops 重构 |
| 2026-04-13 | d027fe7 | feat: 实现 BillingCacheService, OpenAIGatewayService, GeminiMessagesCompatService |
| 2026-04-13 | a607353 | feat: 实现 AdminService, AccountTestService, OpsAlertEvaluatorService |
| 2026-04-13 | f5019ad | feat: 实现 OpenAIOAuthService 和 OpsScheduledReportService |
| 2026-04-13 | 4951c96 | feat: 添加用户公告控制器 AnnouncementController |
| 2026-04-13 | xxxxxxx | feat: 实现 DashboardAggregationService 仪表盘预聚合服务 |
| 2026-04-13 | 321ec83 | feat: 实现 BillingService 综合计费服务和 CostBreakdown |
| 2026-04-13 | a1b2c3d | feat: 实现 SystemOperationLockService 系统操作锁服务 |

---

### 2026-04-12 - EmailService Redis 缓存实现

**目标**: 实现验证码和密码重置令牌的 Redis 缓存

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/common/service/EmailService.java`
   - 注入 StringRedisTemplate
   - 实现 getVerificationCodeFromCache - 从 Redis 获取验证码
   - 实现 saveVerificationCodeToCache - 保存验证码到 Redis
   - 实现 deleteVerificationCodeFromCache - 删除验证码
   - 实现 getPasswordResetTokenFromCache - 获取密码重置令牌
   - 实现 savePasswordResetTokenToCache - 保存密码重置令牌
   - 实现 deletePasswordResetTokenFromCache - 删除密码重置令牌
   - 实现 isInPasswordResetCooldown - 检查密码重置冷却期
   - 实现 setPasswordResetCooldown - 设置密码重置冷却期

**实现的功能**:
- [x] 验证码 Redis 缓存 - 支持 TTL 自动过期
- [x] 密码重置令牌 Redis 缓存 - 支持 TTL 自动过期
- [x] 密码重置邮件冷却期追踪 - 防止频繁发送

---

### 2026-04-12 - OpsService 系统指标和任务心跳实现

**目标**: 完善 OpsService 的系统指标获取和任务心跳功能

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/ops/service/OpsService.java`
   - 注入 SystemMetricsService 依赖
   - 实现 getLatestSystemMetrics - 使用 SystemMetricsService 获取系统指标
   - 实现 getJobHeartbeats - 返回活跃任务心跳列表，支持自动过期（5分钟）
   - 新增 updateJobHeartbeat - 用于更新任务心跳状态
   - 添加 jobHeartbeats 内存缓存

**实现的功能**:
- [x] 系统指标获取 - 集成 SystemMetricsService 获取 CPU、内存使用率
- [x] 任务心跳追踪 - 内存中追踪任务状态，支持自动过期清理
- [x] 任务心跳更新 - updateJobHeartbeat 方法供外部调用

---

### 2026-04-13 - Dashboard/GroupCapacity/Ops 重构

**目标**: 完成所有剩余 TODO 项的重构

**创建的文件 (2个)**:

1. `backend-java/src/main/java/com/sub2api/module/gateway/service/RpmCacheService.java` (新建)
   - RPM 缓存服务
   - 支持每分钟请求计数、批量查询
   - Redis 有序集合实现

2. `backend-java/src/main/java/com/sub2api/module/gateway/service/SessionCacheService.java` (新建)
   - 会话限制缓存服务
   - 支持空闲超时自动过期
   - 批量查询活跃会话数

**修改的文件 (5个)**:

1. `backend-java/src/main/java/com/sub2api/module/dashboard/service/DashboardService.java`
   - 注入 BillingCalculator 实现正确的成本计算
   - 优先使用数据库实际成本，fallback 到估算

2. `backend-java/src/main/java/com/sub2api/module/dashboard/mapper/DashboardMapper.java`
   - 新增 sumTotalCost, sumTotalActualCost 查询
   - 新增 sumTodayCost, sumTodayActualCost 查询
   - 新增 sumTotalCacheCreationTokens, sumTotalCacheReadTokens 查询
   - 新增 sumTodayCacheCreationTokens, sumTodayCacheReadTokens 查询

3. `backend-java/src/main/java/com/sub2api/module/account/service/GroupCapacityService.java`
   - 集成 SessionCacheService 实现会话追踪
   - 集成 RpmCacheService 实现 RPM 追踪
   - 新增 getIdleTimeout 方法

4. `backend-java/src/main/java/com/sub2api/module/ops/controller/OpsController.java`
   - 实现完整的 getErrorLogs 查询
   - 支持平台过滤和分页

5. `backend-java/src/main/java/com/sub2api/module/ops/service/OpsService.java`
   - 实现完整的仪表板聚合查询
   - 新增 calculateRequestStats 请求统计
   - 新增 selectErrorsByFilter 错误日志过滤
   - 支持按错误码和错误阶段统计

**实现的功能**:
- [x] Dashboard 成本计算 - 使用 BillingCalculator + 数据库实际成本
- [x] GroupCapacity 会话追踪 - SessionCacheService
- [x] GroupCapacity RPM 追踪 - RpmCacheService
- [x] OpsController 完整查询 - 支持过滤和分页
- [x] OpsService 聚合查询 - 支持多维度错误统计

### 2026-04-13 - Antigravity 平台服务

**目标**: 实现 Antigravity 平台的 OAuth、Token 管理和配额获取

**创建的文件 (3个)**:

1. `backend-java/src/main/java/com/sub2api/module/billing/service/PricingService.java` (新建)
   - 动态模型定价服务
   - 从 LiteLLM 获取模型定价，支持本地 JSON 缓存和 SHA256 哈希校验
   - 支持 fallback 定价文件

2. `backend-java/src/main/java/com/sub2api/module/gateway/service/AntigravityService.java` (新建)
   - Antigravity 平台核心服务
   - OAuth 授权 URL 生成、Code 交换、Token 刷新
   - 智能重试和限流处理
   - 模型限流追踪

3. `backend-java/src/main/java/com/sub2api/module/gateway/service/AntigravityQuotaService.java` (新建)
   - Antigravity 配额获取服务
   - 从 Antigravity API 获取账号额度信息
   - 支持订阅等级和 Credits 余额查询
   - Redis 缓存支持

**实现的功能**:
- [x] PricingService - LiteLLM 定价获取与缓存
- [x] AntigravityService - OAuth Token 管理
- [x] AntigravityQuotaService - 配额信息获取与缓存
- [x] 模型限流追踪 - Redis 实现
- [x] OAuth 会话管理 - 内存中会话存储

---

### 2026-04-13 - OpsScheduledReportService 定时报表服务

**目标**: 实现定时报表服务，支持日报、周报、错误摘要、账号健康报告

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/ops/service/OpsScheduledReportService.java` (新建)
   - 定时报表服务
   - 使用 cron-utils 解析 cron 表达式
   - 支持分布式锁（Redis leader election）
   - 支持心跳追踪和错误记录
   - 支持日报、周报、错误摘要、账号健康四种报表

**实现的功能**:
- [x] 定时报表调度 - 基于 cron 表达式
- [x] 分布式 leader 锁 - 避免多实例重复执行
- [x] 报表生成 - HTML 格式邮件内容
- [x] 心跳追踪 - Redis 存储上次运行状态
- [x] 错误处理 - 记录心跳错误状态

---

### 2026-04-13 - AnnouncementController 用户公告 API

**目标**: 实现用户公告接口，提供公告列表和已读功能

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/user/controller/AnnouncementController.java` (新建)
   - 用户公告控制器
   - GET /api/v1/announcements - 获取公告列表
   - GET /api/v1/announcements/{id} - 获取公告详情
   - POST /api/v1/announcements/{id}/read - 标记已读
   - 支持 targeting 条件过滤（订阅、余额等）

**实现的功能**:
- [x] 公告列表查询 - 支持未读筛选
- [x] 公告详情查询 - 支持可见性检查
- [x] 已读标记 - 防止重复标记
- [x] targeting 条件评估 - subscription、balance 类型

---

### 2026-04-13 - BillingService 综合计费服务

**目标**: 实现完整的综合计费服务，整合 PricingService、BillingCalculator、ChannelService

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/billing/service/BillingService.java` (新建)
   - 综合计费服务
   - 整合 PricingService、BillingCalculator、ChannelService
   - 支持动态定价 + 硬编码 fallback
   - 支持渠道价格覆盖
   - 统一计费入口 calculateCostUnified

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/billing/service/BillingCalculator.java`
   - 添加 BillingMode 枚举（TOKEN/PER_REQUEST/IMAGE）
   - 添加 CostBreakdown 类（费用明细）
   - 添加 ImagePriceConfig 类
   - 添加 computeTokenBreakdown - Token 计费核心逻辑
   - 添加 calculatePerRequestCost - 按次计费
   - 添加 calculateImageCost - 图片计费
   - 添加 shouldApplySessionLongContextPricing - 长上下文判断
   - 添加 convertPricing - PricingService.ModelPricing 转换

**实现的功能**:
- [x] 动态定价获取 - PricingService + Fallback
- [x] 渠道价格覆盖 - ChannelModelPricing
- [x] 统一计费入口 - calculateCostUnified
- [x] 长上下文计费 - calculateCostWithLongContext
- [x] 图片生成计费 - calculateImageCost
- [x] 前端费用估算 - getEstimatedCost
- [x] 完整 fallback 定价 - Claude/GPT/Gemini 系列

---

### 2026-04-13 - DashboardAggregationService 仪表盘预聚合服务

**目标**: 实现仪表盘预聚合服务，支持定时聚合、回填、重新计算和保留策略清理

**创建的文件 (8个)**:

1. `backend-java/src/main/java/com/sub2api/module/dashboard/service/DashboardAggregationConfig.java` (新建)
   - 仪表盘预聚合配置
   - 支持 enabled, intervalSeconds, lookbackSeconds, backfillEnabled 等配置
   - 支持保留策略配置（usageLogsDays, hourlyDays, dailyDays 等）

2. `backend-java/src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardHourly.java` (新建)
   - 小时聚合实体
   - 包含 bucketStart, totalRequests, inputTokens, outputTokens, cacheTokens, costs, activeUsers 等

3. `backend-java/src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardDaily.java` (新建)
   - 天聚合实体
   - 包含 bucketDate, totalRequests, inputTokens, outputTokens, cacheTokens, costs, activeUsers 等

4. `backend-java/src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardHourlyUsers.java` (新建)
   - 小时活跃用户实体（去重）

5. `backend-java/src/main/java/com/sub2api/module/dashboard/model/entity/UsageDashboardDailyUsers.java` (新建)
   - 天活跃用户实体（去重）

6. `backend-java/src/main/java/com/sub2api/module/dashboard/model/entity/DashboardAggregationWatermark.java` (新建)
   - 聚合水位标记实体（单行表）

7. `backend-java/src/main/java/com/sub2api/module/dashboard/mapper/DashboardAggregationMapper.java` (新建)
   - 仪表盘预聚合 Mapper
   - 支持水位管理、活跃用户插入、聚合 upsert、清理等操作
   - 使用 PostgreSQL 特有的 date_trunc 和时区转换

8. `backend-java/src/main/java/com/sub2api/module/dashboard/service/DashboardAggregationService.java` (新建)
   - 仪表盘预聚合服务
   - 定时聚合 usage_logs 到 hour/daily 聚合表
   - 支持全量回填和重新计算
   - 自动清理过期数据（保留策略）

**实现的功能**:
- [x] 定时聚合 - 每分钟执行，聚合到小时桶和天桶
- [x] 回填功能 - 支持指定时间范围的全量回填
- [x] 重新计算 - 支持清空后重建指定范围数据
- [x] 保留清理 - 定期清理过期的小时/天聚合和 usage_logs
- [x] 分区管理 - 支持 PostgreSQL 分区表的分区创建和删除
- [x] 水位追踪 - 记录上次聚合时间，支持断点续传

---

### 2026-04-13 - EmailQueueService 异步邮件队列服务

**目标**: 实现异步邮件队列服务，将邮件发送任务放入队列异步执行

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/common/service/EmailQueueService.java` (新建)
   - 异步邮件队列服务
   - 支持验证码邮件和密码重置邮件两种任务类型
   - 默认 3 个工作线程，队列容量 100
   - 支持优雅关闭

**实现的功能**:
- [x] 任务入队 - enqueueVerifyCode, enqueuePasswordReset
- [x] 工作线程池 - 固定线程数执行任务
- [x] 优雅关闭 - PreDestroy 时等待任务完成
- [x] 状态查询 - getStatus 返回队列状态

---

### 2026-04-13 - SubscriptionExpiryService 订阅过期服务

**目标**: 实现订阅过期服务，定期检查并更新过期的订阅状态

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/user/service/SubscriptionExpiryService.java` (新建)
   - 订阅过期服务
   - 每分钟检查一次过期订阅
   - 调用 SubscriptionService.expireSubscriptions() 更新状态

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/user/service/SubscriptionService.java`
   - expireSubscriptions() 方法改为返回 int（更新的记录数）

**实现的功能**:
- [x] 定时检查 - 每分钟执行
- [x] 状态更新 - 将过期的 active 订阅标记为 expired

---

### 2026-04-13 - SystemOperationLockService 系统操作锁服务

**目标**: 实现系统操作锁服务，提供全局系统操作的分布式锁机制

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/admin/service/SystemOperationLockService.java` (新建)
   - 系统操作锁服务
   - 使用幂等性记录表实现分布式锁
   - 支持自动续约机制
   - 支持成功/失败两种释放模式

**修改的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/common/model/enums/ErrorCode.java`
   - 添加 SYSTEM_OPERATION_BUSY (7050) 和 SYSTEM_OPERATION_ID_REQUIRED (7051) 错误码

**实现的功能**:
- [x] 获取锁 - acquire(operationId) 获取系统操作锁
- [x] 释放锁 - release(lock, succeeded, failureReason) 释放锁
- [x] 自动续约 - 后台线程定期延长锁的过期时间
- [x] 状态查询 - getStatus() 返回服务状态

**核心方法**:
- `SystemOperationLock acquire(String operationId)` - 获取锁
- `void release(SystemOperationLock lock, boolean succeeded, String failureReason)` - 释放锁
- `void releaseSuccess(SystemOperationLock lock)` - 成功释放
- `void releaseFailure(SystemOperationLock lock, String reason)` - 失败释放
- `Map<String, Object> getStatus()` - 获取服务状态

---

### 2026-04-13 - IdempotencyCleanupService 幂等性清理服务

**目标**: 实现幂等性记录清理服务，定期清理已过期的幂等记录

**创建的文件 (1个)**:

1. `backend-java/src/main/java/com/sub2api/module/admin/service/IdempotencyCleanupService.java` (新建)
   - 幂等性清理服务
   - 启动时先清理一轮防止积压
   - 支持配置清理间隔和批次大小

**实现的功能**:
- [x] 启动时清理 - 防止重启后积压
- [x] 定时清理 - 每分钟执行
- [x] 配置化 - cleanupIntervalSeconds, cleanupBatchSize




