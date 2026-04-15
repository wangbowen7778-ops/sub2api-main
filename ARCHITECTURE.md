# Architecture Design

## 1. System Overview

```
                        ┌─────────────────────────────────────────────┐
                        │               Sub2API Platform               │
                        ├─────────────────────────────────────────────┤
                        │                                              │
  ┌──────────┐          │  ┌──────────┐  ┌──────────┐  ┌──────────┐  │          ┌──────────────┐
  │  Vue 3   │──────────│─▶│  Auth    │  │ Gateway  │  │  Admin   │  │          │   Claude     │
  │ Frontend │          │  │  Filter  │─▶│  Proxy   │─▶│  Panel   │  │          │   OpenAI     │
  └──────────┘          │  └──────────┘  └────┬─────┘  └──────────┘  │          │   Gemini     │
                        │                     │                       │    ┌────▶│   Antigravity│
  ┌──────────┐          │  ┌──────────┐  ┌────▼─────┐  ┌──────────┐  │    │     └──────────────┘
  │ API Key  │──────────│─▶│ Rate     │  │ Account  │  │ Billing  │──│────┘
  │ Clients  │          │  │ Limiter  │  │ Selector │  │ Service  │  │
  └──────────┘          │  └──────────┘  └────┬─────┘  └──────────┘  │
                        │                     │                       │
                        │  ┌──────────┐  ┌────▼─────┐  ┌──────────┐  │
                        │  │ Dashboard │  │ Failover │  │   Ops    │  │
                        │  │ Agg.     │  │ Service  │  │ Monitor  │  │
                        │  └──────────┘  └──────────┘  └──────────┘  │
                        │                                              │
                        │  ┌──────────────────────────────────────┐   │
                        │  │   PostgreSQL  │   Redis   │  SMTP    │   │
                        │  └──────────────────────────────────────┘   │
                        └─────────────────────────────────────────────┘
```

## 2. Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Java 21 (LTS) | Runtime |
| Framework | Spring Boot 3.4 | Application framework |
| ORM | MyBatis-Plus 3.5.7 | Database access with auto-fill |
| DI | Spring DI | Native dependency injection |
| Database | PostgreSQL 15+ | Primary data store |
| Cache | Redis 7+ (Spring Data Redis) | Session, rate limiting, caching |
| Security | Spring Security + JWT (jjwt 0.12.6) | Authentication |
| API Docs | SpringDoc OpenAPI 3 | Swagger UI |
| Build | Maven 3.9+ | Build tool |
| HTTP Client | Spring WebClient (Reactor Netty) | Upstream API calls |
| WebSocket | Spring WebSocket + STOMP | Real-time communication |
| Logging | SLF4J + Logback | Structured logging |
| Pool | HikariCP | Connection pooling (Spring Boot default) |
| Utils | Hutool 5.8.26 | General utilities |
| Code Style | Alibaba Java Guidelines | Checkstyle enforcement |

## 3. Module Architecture

### 3.1 API Gateway Request Flow

```
Client Request
       │
       ▼
┌─────────────────────┐
│  Spring Security    │  JWT or API Key authentication
│  Filter Chain       │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  RateLimitService   │  Sliding window rate limiting (Redis)
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  GatewayController  │  Route to correct platform handler
│  /v1/messages       │  Claude: POST /v1/messages (SSE)
│  /v1/chat/complete  │  OpenAI: POST /v1/chat/completions
│  /v1/responses      │  OpenAI: POST /v1/responses
│  /v1beta/**         │  Gemini: passthrough
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  ProxyService       │  Core routing logic
│  ┌─────────────────┐│
│  │ Channel mapping ││  Resolve model → upstream model
│  │ Account select  ││  Pick best account (weighted, lowest-usage)
│  │ Concurrency     ││  Acquire slots (Redis sorted sets)
│  │ Send request    ││  Forward to upstream with retry
│  │ Failover        ││  Switch account on error (max 2 retries)
│  │ Billing         ││  Calculate and record token costs
│  │ Release slots   ││  Release concurrency slots
│  └─────────────────┘│
└─────────────────────┘
```

### 3.2 Authentication Architecture

```
┌───────────────────────────────────────────────────────────┐
│                    Security Filter Chain                    │
├───────────────────────────────────────────────────────────┤
│                                                            │
│  Request ──▶ JwtAuthenticationFilter                      │
│              │  Extracts Bearer token from Authorization   │
│              │  Validates via JwtService                   │
│              │  Sets SecurityContext                       │
│              │  Skips: /auth/**, /health, /v1/**          │
│              ▼                                             │
│             ApiKeyAuthenticationFilter                     │
│              │  Extracts X-API-Key header or ?api_key      │
│              │  Validates via ApiKeyCacheService (L1 mem)  │
│              │  Falls back to ApiKeyService (DB)           │
│              │  Only active on: /v1/**, /v1beta/**,       │
│              │                  /antigravity/**            │
│              ▼                                             │
│             SecurityConfig                                 │
│              │  /admin/** → requires ROLE_ADMIN            │
│              │  CORS: wildcard origins allowed             │
│              │  CSRF: disabled (stateless)                 │
│              │  Session: STATELESS                         │
│                                                            │
├───────────────────────────────────────────────────────────┤
│  OAuth Providers:                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │Anthropic │ │ OpenAI   │ │ Google   │ │Linux.do  │     │
│  │Handler   │ │ Handler  │ │ Handler  │ │ (OIDC)   │     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
└───────────────────────────────────────────────────────────┘
```

### 3.3 Account Selection Algorithm

```
selectAccount(groupId, platform, model)
       │
       ├─── Check sticky session (Redis)
       │    └─── If found & valid → use cached account
       │         └─── Check: shouldClearStickySession()
       │              ├── Account status (error/disabled?)
       │              ├── Temp unschedulable?
       │              └── Model rate limited?
       │
       ├─── Get available accounts by group
       │    └─── Filter: active + correct platform + not temp-banned
       │
       ├─── Usage prefetch (batch, avoid N+1)
       │    └─── UsagePrefetchService → Redis cache
       │
       └─── Select by lowest usage (weighted)
            ├── Calculate: windowCost / (dailyLimit * loadFactor)
            ├── Apply priority weighting
            └── Return account with lowest utilization ratio
```

### 3.4 Billing Pipeline

```
Request Completed
       │
       ▼
┌─────────────────────┐
│  BillingService     │  Unified billing entry
│  calculateCost()    │
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    ▼           ▼
┌────────┐ ┌────────────┐
│Pricing │ │ Channel    │  Channel-specific price overrides
│Service │ │ Service    │
└───┬────┘ └─────┬──────┘
    │            │
    ▼            ▼
┌─────────────────────┐
│  BillingCalculator  │
│  ┌────────────────┐ │
│  │ Token billing  │ │  input/output/cache token pricing
│  │ Per-request    │ │  flat rate per API call
│  │ Image billing  │ │  image generation pricing
│  │ Long-context   │ │  context > threshold → higher rate
│  └────────────────┘ │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  UsageLog record    │  Write to usage_logs table
│  User balance update│  Deduct from user balance
│  Redis cache update │  Update billing cache
└─────────────────────┘
```

### 3.5 Failover Mechanism

```
ProxyService.sendRequest()
       │
       ├── Success → return response
       │
       └── Error
            │
            ▼
      ┌─────────────────┐
      │ Is retryable?   │  429, 500, 502, 503, 504, timeout
      ├── Yes ──────────┤
      │                 ▼
      │   Same-account retry (max 3, 500ms delay)
      │         │
      │         ├── Success → return
      │         └── Still failing
      │                 │
      │                 ▼
      │   Mark account temp-unschedulable
      │   (duration based on error type)
      │         │
      │         ▼
      │   Select new account (failover)
      │   (max 2 account switches)
      │         │
      │         ├── Success → return
      │         └── Exhausted → return last error
      │
      └── No (4xx client error) → return error immediately
```

## 4. Database Design

### Entity Mapping (Go Ent → Java MyBatis-Plus)

| Go Ent Schema | Java Entity | DB Table | Module |
|---------------|-------------|----------|--------|
| account | `Account` | `account` | account |
| account_group | `AccountGroup` | `account_groups` | account |
| group | `Group` | `app_group` | account |
| proxy | `Proxy` | `proxy` | account |
| user | `User` | `users` | user |
| user_subscription | `UserSubscription` | `user_subscriptions` | user |
| user_allowed_group | `UserAllowedGroup` | `user_allowed_groups` | user |
| announcement | `Announcement` | `announcement` | admin |
| announcement_read | `AnnouncementRead` | `announcement_reads` | user |
| api_key | `ApiKey` | `api_key` | apikey |
| usage_log | `UsageLog` | `usage_log` | billing |
| promo_code | `PromoCode` | `promo_code` | billing |
| promo_code_usage | `PromoCodeUsage` | `promo_code_usages` | billing |
| redeem_code | `RedeemCode` | `redeem_code` | billing |
| channel | `Channel` | `channel` | channel |
| channel_model_pricing | `ChannelModelPricing` | `channel_model_pricing` | channel |
| pricing_interval | `PricingInterval` | `pricing_interval` | channel |
| setting | `Setting` | `setting` | admin |
| error_passthrough_rule | `ErrorPassthroughRule` | `error_passthrough_rules` | admin |
| idempotency_record | `IdempotencyRecord` | `idempotency_records` | admin |
| scheduled_test_plan | `ScheduledTestPlan` | `scheduled_test_plans` | admin |
| scheduled_test_result | `ScheduledTestResult` | `scheduled_test_results` | admin |
| tls_fingerprint_profile | `TLSFingerprintProfile` | `tls_fingerprint_profiles` | admin |
| ops_error_log | `OpsErrorLog` | `ops_error_logs` | ops |
| usage_dashboard_hourly | `UsageDashboardHourly` | `usage_dashboard_hourly` | dashboard |
| usage_dashboard_daily | `UsageDashboardDaily` | `usage_dashboard_daily` | dashboard |

### Key Relationships

```
User ─── 1:N ──→ ApiKey
User ─── 1:N ──→ UserSubscription
User ─── N:M ──→ Group (via user_allowed_groups)

Account ─── N:M ──→ Group (via account_groups)
Account ─── N:1 ──→ Proxy

Channel ─── N:M ──→ Group (via channel_groups)
Channel ─── 1:N ──→ ChannelModelPricing ─── 1:N ──→ PricingInterval

UsageLog ──→ references: User, ApiKey, Account, Group
```

## 5. Caching Strategy

| Data | Cache Layer | TTL | Invalidation |
|------|-------------|-----|-------------|
| API Key auth | L1 Memory + L2 Redis | 5 min | On key update/delete |
| System settings | Redis | 60s | On setting update |
| Model pricing | Redis | 1 hour | On manual refresh |
| Dashboard stats | Redis | 5 min | On manual refresh |
| Billing cache | Redis | Per-request | On billing update |
| Concurrency slots | Redis sorted set | Auto-expire | On request complete |
| RPM counter | Redis sorted set | 1 min window | Auto-expire |
| Session cache | Redis | Idle timeout | On session end |
| Error passthrough rules | Local + Redis | On reload | Manual cache invalidate |
| Antigravity quota | Redis | 5 min | Auto-expire |
| Identity cache | Redis | 30 min | On identity update |

## 6. Configuration Structure

```yaml
# application.yml key sections
server:
  port: 8080

spring:
  datasource:                    # PostgreSQL via HikariCP
  data.redis:                    # Redis via Lettuce
  security.oauth2.client:        # OAuth2 provider configs

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  global-config:
    db-config:
      logic-delete-field: deletedAt

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 86400     # 24h
  refresh-token-expiration: 604800   # 7d

gateway:
  default-timeout: 120               # seconds
  max-retries: 3
  stream-timeout: 300                # seconds
  max-request-body-size: 268435456   # 256MB
```

## 7. Frontend Compatibility

The Vue 3 frontend requires **no changes** when switching from Go backend to Java backend:

- Same API route structure (`/v1/*`, `/admin/*`, `/auth/*`)
- Same request/response JSON format
- Same authentication flow (JWT + API Key)
- Same WebSocket endpoints
- Same SSE streaming format
- Docker deployment: just replace the backend image
