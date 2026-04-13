# Backend-Java 重构任务清单

> 最后更新: 2026-04-13
> 状态说明: ✅ 已完成 | 🔄 进行中 | ⏳ 待处理

## 一、核心服务 (Core Services)

### 1.1 Account 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| AccountService | ✅ 已完成 | 账号管理服务 |
| AccountHealthService | ✅ 已完成 | 账号健康检查 |
| AccountRefreshService | ✅ 已完成 | 账号凭证刷新 |
| AccountExpiryService | ✅ 已完成 | 账号过期检查 |
| AccountSelector | ✅ 已完成 | 账号选择器 |
| AccountTestService | ✅ 已完成 | 账号连接测试 |

### 1.2 Admin 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| AdminService | ✅ 已完成 | 管理员面板核心服务 |
| AnnouncementService | ✅ 已完成 | 公告管理服务 |
| AnnouncementController | ✅ 已完成 | 用户公告 API |
| AnnouncementAdminController | ✅ 已完成 | 管理员公告 API |
| IdempotencyService | ✅ 已完成 | 幂等性服务 |
| PromoCodeService | ✅ 已完成 | 兑换码服务 |
| RedeemCodeService | ✅ 已完成 | 充值码服务 |
| GroupCapacityService | ✅ 已完成 | 分组容量服务 |

### 1.3 Auth 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| AuthService | ✅ 已完成 | 认证服务 |
| JwtService | ✅ 已完成 | JWT 令牌服务 |
| TOTPService | ✅ 已完成 | TOTP 验证码服务 |
| OAuthService | ✅ 已完成 | OAuth 服务 |
| GoogleOAuthHandler | ✅ 已完成 | Google OAuth 处理器 |
| OpenAIOAuthHandler | ✅ 已完成 | OpenAI OAuth 处理器 |
| AnthropicOAuthHandler | ✅ 已完成 | Anthropic OAuth 处理器 |
| OpenAIOAuthService | ✅ 已完成 | OpenAI OAuth 认证服务 |

### 1.4 Gateway 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| OpenAIGatewayService | ✅ 已完成 | OpenAI 网关服务 |
| GeminiMessagesCompatService | ✅ 已完成 | Gemini 兼容性服务 |
| AntigravityService | ✅ 已完成 | Antigravity 网关服务 |
| AntigravityQuotaService | ✅ 已完成 | Antigravity 配额服务 |
| FailoverService | ✅ 已完成 | 故障转移服务 |
| ConcurrencyService | ✅ 已完成 | 并发控制服务 |
| SessionCacheService | ✅ 已完成 | 会话缓存服务 |
| UsagePrefetchService | ✅ 已完成 | 用量预取服务 |
| ClaudeCodeValidator | ✅ 已完成 | Claude Code 验证 |
| ProxyService | ✅ 已完成 | 代理服务 |
| ProxyLatencyService | ✅ 已完成 | 代理延迟服务 |
| ProxyConfigService | ✅ 已完成 | 代理配置服务 |
| RpmCacheService | ✅ 已完成 | RPM 缓存服务 |

### 1.5 Billing 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| BillingCacheService | ✅ 已完成 | 计费缓存服务 |
| PricingService | ✅ 已完成 | 模型定价服务 |
| BillingCalculator | ✅ 已完成 | 计费计算器 |
| BillingService | ✅ 已完成 | 综合计费服务 |

### 1.6 Ops 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| OpsService | ✅ 已完成 | Ops 监控服务 |
| OpsAlertEvaluatorService | ✅ 已完成 | 告警评估服务 |
| OpsScheduledReportService | ✅ 已完成 | 定时报表服务 |
| SystemMetricsService | ✅ 已完成 | 系统指标服务 |

### 1.7 User 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| UserService | ✅ 已完成 | 用户服务 |
| SubscriptionService | ✅ 已完成 | 订阅服务 |
| AnnouncementReadMapper | ✅ 已完成 | 公告已读映射 |

### 1.8 API Key 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| ApiKeyService | ✅ 已完成 | API Key 服务 |
| ApiKeyCacheService | ✅ 已完成 | API Key 缓存服务 |
| RedisApiKeyAuthCache | ✅ 已完成 | Redis API Key 认证缓存（L2 Redis 缓存，基础功能完整） |

### 1.9 Channel 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| ChannelService | ✅ 已完成 | 渠道服务 |

### 1.10 Dashboard 相关
| 任务 | 状态 | 说明 |
|------|------|------|
| DashboardService | ✅ 已完成 | 仪表盘服务 |
| DashboardAggregationService | ✅ 已完成 | 仪表盘预聚合服务 |
| GroupService | ✅ 已完成 | 分组服务 |

### 1.11 其他服务
| 任务 | 状态 | 说明 |
|------|------|------|
| EmailService | ✅ 已完成 | 邮件服务 |
| SettingService | ✅ 已完成 | 设置服务 |
| DeferredService | ✅ 已完成 | 延迟服务 |
| GitHubReleaseService | ✅ 已完成 | GitHub 发布服务 |
| TLSFingerprintProfileService | ✅ 已完成 | TLS 指纹服务 |
| TurnstileService | ✅ 已完成 | Turnstile 验证服务 |
| RateLimitService | ✅ 已完成 | 限流服务 |
| ScheduledTestService | ✅ 已完成 | 定时测试服务 |
| UsageLogService | ✅ 已完成 | 用量日志服务 |
| IdentityService | ✅ 已完成 | 身份服务 |
| IdempotencyCleanupService | ⏳ 待处理 | 幂等性清理服务 |
| SubscriptionExpiryService | ⏳ 待处理 | 订阅过期服务 |

---

## 二、缺失服务清单 (按优先级)

### P0 - 关键 (影响核心功能)
| 优先级 | 服务 | Go 对应文件 | 状态 |
|--------|------|-------------|------|
| P0 | **BillingService** | billing_service.go | ✅ 已完成 |
| P0 | **DashboardAggregationService** | dashboard_aggregation_service.go | ✅ 已完成 |
| P0 | **TokenRefreshService** | token_refresh_service.go | ✅ 已完成 (AccountRefreshService) |

### P1 - 高优先级 (影响生产稳定性)
| 优先级 | 服务 | Go 对应文件 | 状态 |
|--------|------|-------------|------|
| P1 | **APIKeyAuthCacheService** | api_key_auth_cache.go | ⏳ 待处理 |
| P1 | **EmailQueueService** | email_queue_service.go | ⏳ 待处理 |
| P1 | **SubscriptionExpiryService** | subscription_expiry_service.go | ⏳ 待处理 |
| P1 | **IdempotencyCleanupService** | idempotency_cleanup_service.go | ⏳ 待处理 |

### P2 - 中优先级 (增强功能)
| 优先级 | 服务 | Go 对应文件 | 状态 |
|--------|------|-------------|------|
| P2 | **GeminiOAuthService** | gemini_oauth_service.go | ⏳ 待处理 |
| P2 | **SystemOperationLockService** | system_operation_lock_service.go | ⏳ 待处理 |
| P2 | **UsageCleanupService** | usage_cleanup_service.go | ⏳ 待处理 |
| P2 | **SchedulerSnapshotService** | scheduler_snapshot_service.go | ⏳ 待处理 |
| P2 | **SchedulerCache** | scheduler_cache.go | ⏳ 待处理 |

### P3 - 低优先级 (可选功能)
| 优先级 | 服务 | Go 对应文件 | 状态 |
|--------|------|-------------|------|
| P3 | **BedrockSignerService** | bedrock_signer.go | ⏳ 待处理 |
| P3 | **BedrockRequestService** | bedrock_request.go | ⏳ 待处理 |
| P3 | **BedrockStreamService** | bedrock_stream.go | ⏳ 待处理 |
| P3 | **BackupService** | backup_service.go | ⏳ 待处理 |
| P3 | **OpenAIAccountScheduler** | openai_account_scheduler.go | ⏳ 待处理 |

---

## 三、已完成任务汇总

| 模块 | 已完成 | 总计 | 完成率 |
|------|--------|------|--------|
| Account | 6 | 6 | 100% |
| Admin | 8 | 8 | 100% |
| Auth | 8 | 8 | 100% |
| Gateway | 13 | 13 | 100% |
| Billing | 4 | 4 | 100% |
| Ops | 4 | 4 | 100% |
| User | 3 | 3 | 100% |
| API Key | 3 | 3 | 100% |
| Channel | 1 | 1 | 100% |
| Dashboard | 3 | 3 | 100% |
| 其他服务 | 10 | 10 | 100% |
| **缺失服务** | 0 | 13 | 0% |

---

## 四、Git 提交历史

```
8a0ad2b feat: 实现 DashboardAggregationService 仪表盘预聚合服务
4951c96 feat: 添加用户公告控制器 AnnouncementController
f5019ad feat: 实现 OpenAIOAuthService 和 OpsScheduledReportService
d027fe7 feat: 实现 BillingCacheService, OpenAIGatewayService, GeminiMessagesCompatService
a607353 feat: 实现 AdminService, AccountTestService, OpsAlertEvaluatorService
```

---

## 五、下一步计划

1. **P0 优先级**: 实现 DashboardAggregationService（仪表盘预聚合）
2. **P0 优先级**: 实现 TokenRefreshService（OAuth token 刷新）
3. **P1 优先级**: 实现 APIKeyAuthCacheService（认证缓存）
4. **P1 优先级**: 实现 APIKeyAuthCacheService（认证缓存）
5. **P1 优先级**: 实现 EmailQueueService（异步邮件队列）
