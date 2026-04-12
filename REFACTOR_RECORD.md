# Java重构记录 (backend-java)

## 项目概述
- **目标**: 将 Go backend 重构为 Java/Spring Boot 实现
- **参考项目**: `sub2api-main/backend` (Go)
- **重构项目**: `sub2api-main/backend-java` (Java)

## 当前状态
- **覆盖率**: ~40-50%
- **状态**: 持续重构中，核心功能逐步完善
- **最近更新**: 2026-04-12 - 并发控制、Ops监控、延迟追踪、用量预取

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
