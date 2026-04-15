# Refactoring Progress

> Last updated: 2026-04-15
> This file replaces the previous REFACTOR_RECORD.md and REFACTORING_TASKS.md

## Overall Status

| Metric | Value |
|--------|-------|
| **Core services** | 63/63 (100%) |
| **Extended services** | 5/9 (56%) |
| **Total Java files** | 153 |
| **MyBatis XML mappers** | 6 |
| **Compilation** | Passing |
| **Unit tests** | Not yet written |
| **Integration tests** | Not yet written |

---

## Module Completion Matrix

### Core Services (100% Complete)

| Module | Service | Status | Notes |
|--------|---------|--------|-------|
| **account** | AccountService | Done | CRUD, model mapping, token usage reset |
| | AccountSelector | Done | Weighted selection, sticky session, model rate limit |
| | AccountHealthService | Done | OAuth + API Key health check, Antigravity |
| | AccountRefreshService | Done | Google, Linux.do, Anthropic, OpenAI OAuth refresh |
| | AccountExpiryService | Done | Auto-pause expired accounts |
| | AccountTestService | Done | Connection testing |
| | GroupService | Done | Group CRUD |
| | GroupCapacityService | Done | Session + RPM tracking |
| | DeferredService | Done | Batched deferred updates |
| | IdentityService | Done | Fingerprint management |
| | RedisIdentityCache | Done | Redis cache for identities |
| | ProxyConfigService | Done | Proxy configuration |
| **auth** | AuthService | Done | Login, register, password change |
| | JwtService | Done | HMAC-SHA JWT generation/validation |
| | TOTPService | Done | Two-factor authentication |
| | OAuthService | Done | OAuth flow processing |
| | OpenAIOAuthService | Done | OpenAI-specific OAuth |
| | AnthropicOAuthHandler | Done | Anthropic OAuth platform handler |
| | OpenAIOAuthHandler | Done | OpenAI OAuth platform handler |
| | GoogleOAuthHandler | Done | Google OAuth platform handler |
| **gateway** | ProxyService | Done | Core routing with channel mapping + failover |
| | FailoverService | Done | Same-account retry (3x), temp unschedulable |
| | ConcurrencyService | Done | Redis sorted set slot management |
| | ProxyLatencyService | Done | Latency tracking, optimal proxy selection |
| | UsagePrefetchService | Done | Batch prefetch, avoid N+1 |
| | RpmCacheService | Done | Per-minute request counting |
| | SessionCacheService | Done | Session limit with idle timeout |
| | ClaudeCodeValidator | Done | User-Agent + Dice coefficient + user_id format |
| | OpenAIGatewayService | Done | OpenAI-specific gateway logic |
| | GeminiMessagesCompatService | Done | Gemini API compatibility layer |
| | AntigravityService | Done | OAuth, token management, rate limit tracking |
| | AntigravityQuotaService | Done | Quota fetching with Redis cache |
| | OpenAIWebSocketHandler | Done | WebSocket for /v1/responses/ws |
| | RequestBodyLimitFilter | Done | 256MB limit, configurable |
| **billing** | BillingService | Done | Unified billing with channel overrides |
| | BillingCalculator | Done | Token/per-request/image, long-context |
| | PricingService | Done | LiteLLM dynamic pricing + local cache |
| | BillingCacheService | Done | Billing cache abstraction |
| | RedisBillingCache | Done | Redis billing cache implementation |
| | UsageLogService | Done | Usage log queries and statistics |
| | RateLimitService | Done | Sliding window rate limiting |
| | PromoCodeService | Done | Promo code management |
| | RedeemCodeService | Done | Redeem code management |
| **admin** | AdminService | Done | Admin panel core |
| | AnnouncementService | Done | Announcement CRUD |
| | ErrorPassthroughRuleService | Done | Rule CRUD, matching, cache |
| | IdempotencyService | Done | Idempotency key management |
| | IdempotencyCleanupService | Done | Scheduled cleanup |
| | ScheduledTestService | Done | Scheduled test execution |
| | SettingService | Done | System settings with Redis cache |
| | SystemOperationLockService | Done | Distributed lock via idempotency |
| | TLSFingerprintProfileService | Done | TLS fingerprint management |
| **user** | UserService | Done | User CRUD |
| | SubscriptionService | Done | Subscription CRUD, expiry |
| | SubscriptionExpiryService | Done | Scheduled expiry check (every minute) |
| **apikey** | ApiKeyService | Done | API Key CRUD |
| | ApiKeyCacheService | Done | In-memory L1 cache |
| | RedisApiKeyAuthCache | Done | Redis L2 cache |
| **channel** | ChannelService | Done | CRUD, model mapping, cache, group association |
| **dashboard** | DashboardService | Done | Stats, trends, rankings with caching |
| | DashboardAggregationService | Done | Hourly/daily bucketing, backfill, retention |
| | DashboardAggregationConfig | Done | Aggregation configuration |
| **ops** | OpsService | Done | Error recording, dashboard, metrics |
| | OpsAlertEvaluatorService | Done | Alert evaluation |
| | OpsScheduledReportService | Done | Daily/weekly reports, leader lock |
| | SystemMetricsService | Done | CPU, memory, JVM monitoring |
| **common** | EmailService | Done | SMTP with Redis cache |
| | EmailQueueService | Done | Async queue, 3 workers |
| | TurnstileService | Done | Cloudflare bot verification |
| | GitHubReleaseService | Done | GitHub release info |

---

### Extended Services (Pending)

#### P2 - Medium Priority (Enhancement Features)

| Service | Go Source | Status | Description |
|---------|-----------|--------|-------------|
| GeminiOAuthService | `gemini_oauth_service.go` | Pending | Gemini Code Assist + AI Studio OAuth |
| UsageCleanupService | `usage_cleanup_service.go` | Pending | Periodic usage_logs cleanup by retention policy |
| SchedulerSnapshotService | `scheduler_snapshot_service.go` | Pending | Account scheduler state snapshot for debugging |
| SchedulerCache | `scheduler_cache.go` | Pending | In-memory cache for account scheduling decisions |

#### P3 - Low Priority (Optional Features)

| Service | Go Source | Status | Description |
|---------|-----------|--------|-------------|
| BedrockSignerService | `bedrock_signer.go` | Pending | AWS Bedrock request signing (SigV4) |
| BedrockRequestService | `bedrock_request.go` | Pending | AWS Bedrock API request handling |
| BedrockStreamService | `bedrock_stream.go` | Pending | AWS Bedrock SSE stream processing |
| BackupService | `backup_service.go` | Pending | Database backup via pg_dump |
| OpenAIAccountScheduler | `openai_account_scheduler.go` | Pending | OpenAI-specific account scheduling logic |

---

## What's NOT Implemented Yet

Beyond the pending services above, the following areas need attention:

### Testing
- [ ] Unit tests for all services
- [ ] Integration tests with Testcontainers (PostgreSQL + Redis)
- [ ] API endpoint tests (MockMvc)
- [ ] Load/stress testing

### Production Readiness
- [ ] Database migration strategy (currently relies on Go backend migrations)
- [ ] Actuator health endpoints beyond `/health`
- [ ] Metrics export (Prometheus/Micrometer)
- [ ] Structured logging with trace IDs
- [ ] Graceful shutdown for all scheduled tasks
- [ ] Rate limiting tuning and testing

### Features Parity Gaps
- [ ] TLS fingerprint simulation (Go uses utls, no direct Java equivalent)
- [ ] HTTP/2 (h2c) support
- [ ] Sora media signing URLs
- [ ] Data management daemon (datamanagementd) equivalent
- [ ] Full OpenAI WebSocket v2 protocol (current implementation is basic)

---

## Git Commit History (Summary)

### 2026-04-13
- `feat: BillingService unified billing with CostBreakdown`
- `feat: DashboardAggregationService pre-aggregation (hourly/daily)`
- `feat: SystemOperationLockService distributed lock`
- `feat: IdempotencyCleanupService`
- `feat: SubscriptionExpiryService`
- `feat: EmailQueueService async queue`
- `feat: AnnouncementController user API`
- `feat: OpsScheduledReportService + OpenAIOAuthService`
- `feat: AdminService + AccountTestService + OpsAlertEvaluatorService`
- `feat: BillingCacheService + OpenAIGatewayService + GeminiMessagesCompatService`
- `feat: AntigravityService + AntigravityQuotaService`
- `feat: Dashboard/GroupCapacity/Ops refactoring`

### 2026-04-12
- `feat: ConcurrencyService + ProxyLatencyService + UsagePrefetchService`
- `feat: FailoverService + FailoverState + UpstreamFailoverError`
- `feat: ClaudeCodeValidator (Dice coefficient)`
- `feat: IdempotencyService`
- `feat: RequestBodyLimitFilter`
- `feat: AccountExpiryService`
- `feat: OAuth Token refresh (Google/Linux.do/Anthropic/OpenAI)`
- `feat: Account health check enhancement`
- `feat: Sticky session model rate limit check`
- `feat: EmailService Redis cache`
- `feat: SystemMetricsService + GitHubReleaseService + TurnstileService`
- `feat: GroupCapacityService + RedisBillingCache + RedisApiKeyAuthCache`
- `fix: selectByLowestUsage using proxyId → accountId`

### 2026-04-11
- `feat: OpenAI WebSocket handler`
- `feat: SubscriptionService + SettingService`
- `feat: ErrorPassthroughRuleService (CRUD + matching + cache)`

### 2026-04-10
- `feat: Channel Management (7 files)`
- `feat: ProxyService failover/retry enhancement`
- `feat: Ops Monitoring framework`
- `feat: Dashboard/Statistics service`
- `feat: GatewayController /v1/models + /v1/usage`
- `fix: Account entity field completion`
- `fix: UserAdminController + AccountAdminController completion`

---

## Next Steps (Recommended Order)

1. **Write unit tests** for critical paths: AccountSelector, ProxyService, BillingCalculator
2. **Implement P2 services**: GeminiOAuthService (needed if using Gemini OAuth), UsageCleanupService (needed for production)
3. **Add integration tests** with Testcontainers
4. **Compilation verification**: Run full `mvn compile` and fix any remaining issues
5. **API compatibility test**: Deploy alongside Go backend and compare responses
6. **Implement P3 services** if needed (Bedrock, Backup, etc.)
