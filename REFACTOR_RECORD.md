# Java重构记录 (backend-java)

## 项目概述
- **目标**: 将 Go backend 重构为 Java/Spring Boot 实现
- **参考项目**: `sub2api-main/backend` (Go)
- **重构项目**: `sub2api-main/backend-java` (Java)

## 当前状态
- **覆盖率**: ~35-45%
- **状态**: 早期阶段，部分核心功能已实现但仍不完整
- **最近更新**: 2026-04-10 - Gateway Controller 增强 (models/usage 端点)

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
- [ ] 与 ProxyService 集成，在请求失败时调用 matchRule
- [ ] 与 OpsService 集成，判断是否跳过监控记录

---

## 后续更新...
