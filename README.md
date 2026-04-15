# Sub2API Java Backend

AI API Gateway Platform - Java backend implementation.

Distributes and manages AI product subscription API quotas. Users access upstream AI services (Claude, OpenAI, Gemini, etc.) via platform-generated API keys with authentication, billing, load balancing, and request forwarding.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| ORM | MyBatis-Plus 3.5 |
| Database | PostgreSQL 15+ |
| Cache | Redis 7+ |
| Security | Spring Security + JWT (jjwt) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9+ |
| HTTP Client | Spring WebClient (Reactor Netty) |
| Deploy | Docker + Docker Compose |

## Prerequisites

- JDK 21+
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+

## Quick Start

```bash
# 1. Configure
cp src/main/resources/application.yml src/main/resources/application-local.yml
# Edit application-local.yml with your DB and Redis connection info

# 2. Build
mvn clean package -DskipTests

# 3. Run
java -jar target/sub2api-backend.jar

# Or with Docker
docker-compose up -d
```

API docs available at: `http://localhost:8080/swagger-ui.html`

## Project Structure

```
src/main/java/com/sub2api/
├── Sub2ApiApplication.java          # Spring Boot entry point
└── module/
    ├── common/                      # Cross-cutting concerns
    │   ├── config/                  # Redis, Security, WebSocket, WebClient, MyBatis
    │   ├── exception/               # GlobalExceptionHandler, BusinessException
    │   ├── model/                   # Result, PageResult, ErrorCode
    │   ├── service/                 # Email, Turnstile, GitHub Release
    │   └── util/                    # DateTime, Encryption, IP utilities
    │
    ├── auth/                        # Authentication & Authorization
    │   ├── controller/              # Login, Register, OAuth endpoints
    │   ├── filter/                  # JWT filter, API Key filter
    │   ├── service/                 # Auth, JWT, TOTP, OAuth services
    │   │   └── platform/            # Anthropic, OpenAI, Google OAuth handlers
    │   └── websocket/               # WebSocket auth interceptor
    │
    ├── user/                        # User Management
    │   ├── controller/              # User self-service, Announcements
    │   ├── mapper/                  # User, Subscription, AnnouncementRead
    │   ├── model/entity/            # User, UserSubscription, AnnouncementRead
    │   └── service/                 # User, Subscription, SubscriptionExpiry
    │
    ├── account/                     # Upstream Account Management (Core)
    │   ├── mapper/                  # Account, AccountGroup, Group, Proxy
    │   ├── model/                   # Account, Group, Proxy entities + enums
    │   └── service/                 # Account CRUD, Selector, Health, Refresh, Expiry, Test
    │
    ├── apikey/                      # API Key Management
    │   ├── mapper/                  # ApiKey mapper
    │   ├── model/                   # ApiKey entity, ApiKeyInfo VO
    │   └── service/                 # ApiKey CRUD, Cache (memory + Redis L2)
    │
    ├── gateway/                     # API Gateway (Core)
    │   ├── controller/              # Gateway, OpenAI, Antigravity endpoints
    │   ├── filter/                  # Request body limit filter
    │   ├── service/                 # Proxy, Failover, Concurrency, Latency, Session,
    │   │                            # RPM, UsagePrefetch, ClaudeCode, OpenAI, Gemini,
    │   │                            # Antigravity, AntigravityQuota
    │   └── websocket/               # OpenAI WebSocket handler
    │
    ├── billing/                     # Billing & Pricing
    │   ├── mapper/                  # UsageLog, PromoCode, RedeemCode
    │   ├── model/                   # Billing entities + BillingStatistics VO
    │   └── service/                 # Billing, Calculator, Pricing, Cache, RateLimit,
    │                                # UsageLog, PromoCode, RedeemCode
    │
    ├── channel/                     # Channel Routing
    │   ├── controller/              # Channel admin API
    │   ├── mapper/                  # Channel, ChannelModelPricing, PricingInterval, ChannelGroup
    │   ├── model/entity/            # Channel, ChannelModelPricing, PricingInterval
    │   └── service/                 # Channel CRUD, model mapping, cache
    │
    ├── dashboard/                   # Analytics & Reporting
    │   ├── controller/              # Dashboard admin API
    │   ├── mapper/                  # Dashboard queries, Aggregation mapper
    │   ├── model/                   # Aggregation entities + stats VOs
    │   └── service/                 # Dashboard stats, Aggregation (hourly/daily), Config
    │
    ├── admin/                       # Admin Panel
    │   ├── controller/              # 12 admin controllers
    │   ├── mapper/                  # Announcement, Setting, ErrorRule, TLS, etc.
    │   ├── model/entity/            # Admin-specific entities
    │   └── service/                 # Admin, Announcement, ErrorRule, Idempotency,
    │                                # ScheduledTest, Setting, SystemLock, TLS
    │
    └── ops/                         # Operations Monitoring
        ├── controller/              # Ops dashboard, errors, metrics
        ├── mapper/                  # Error log mapper
        ├── model/                   # OpsErrorLog entity, OpsDashboardOverview VO
        └── service/                 # Ops, AlertEvaluator, ScheduledReport, SystemMetrics
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `sub2api` |
| `DB_USERNAME` | Database user | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | (none) |
| `JWT_SECRET` | JWT signing secret | **(required)** |
| `ANTHROPIC_CLIENT_ID` | Anthropic OAuth client ID | (optional) |
| `ANTHROPIC_CLIENT_SECRET` | Anthropic OAuth client secret | (optional) |
| `OPENAI_CLIENT_ID` | OpenAI OAuth client ID | (optional) |
| `OPENAI_CLIENT_SECRET` | OpenAI OAuth client secret | (optional) |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID | (optional) |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret | (optional) |

## API Endpoints Overview

### Public

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | User login |
| POST | `/auth/register` | User registration |
| GET | `/health` | Health check |

### API Gateway (API Key auth)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/messages` | Anthropic Claude API (SSE) |
| POST | `/v1/chat/completions` | OpenAI Chat API |
| POST | `/v1/responses` | OpenAI Responses API |
| POST | `/v1/embeddings` | Embeddings API |
| GET | `/v1/models` | List available models |
| GET | `/v1/usage` | API Key usage info |
| ANY | `/v1beta/**` | Gemini API passthrough |
| ANY | `/antigravity/**` | Antigravity API |
| WS | `/v1/responses/ws` | OpenAI WebSocket |

### Admin (JWT + ROLE_ADMIN)

`/admin/users`, `/admin/accounts`, `/admin/groups`, `/admin/channels`, `/admin/api-keys`, `/admin/settings`, `/admin/dashboard`, `/admin/ops`, `/admin/announcements`, `/admin/subscriptions`, `/admin/billing`, `/admin/proxies`, `/admin/error-passthrough-rules`, `/admin/scheduled-tests`, `/admin/tls-fingerprints`

## Docker Deployment

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend

# Stop
docker-compose down
```

Services: backend (8080), PostgreSQL (5432), Redis (6379)

## Documentation

| File | Purpose |
|------|---------|
| [CLAUDE.md](CLAUDE.md) | AI context, coding standards, git conventions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture and design |
| [PROGRESS.md](PROGRESS.md) | Refactoring progress and pending items |
