# Sub2API Java Backend - AI Development Context

> This file is the single source of truth for AI assistants working on this project.
> Read this file first before any code modification.

## Project Overview

Sub2API is an AI API Gateway Platform. This is the **Java rewrite** of the original Go backend (`sub2api-main/backend/`).
The Java version mirrors the Go backend's functionality using Spring Boot + MyBatis-Plus.

- **Original**: Go + Gin + Ent ORM + Google Wire
- **This project**: Java 21 + Spring Boot 3.4 + MyBatis-Plus 3.5
- **Frontend**: Vue 3 + Vite + TailwindCSS (shared, no changes needed)
- **Database**: PostgreSQL 15+ (shared schema from Go migrations)
- **Cache**: Redis 7+

## Quick Reference

| Item | Value |
|------|-------|
| Entry point | `Sub2ApiApplication.java` |
| Base package | `com.sub2api.module` |
| Config | `src/main/resources/application.yml` |
| Build | `mvn clean package -DskipTests` |
| Run | `java -jar target/sub2api-backend.jar` |
| Java version | 21 (runtime), 17 (compiler target in pom.xml) |
| API docs | `http://localhost:8080/swagger-ui.html` |

## Module Map (11 modules)

```
com.sub2api.module/
├── common/      # Cross-cutting: config, exception, util, shared services
├── auth/        # JWT + OAuth + API Key authentication
├── user/        # User CRUD, subscriptions, announcements
├── account/     # Upstream AI account management (core domain)
├── apikey/      # API Key lifecycle and caching
├── gateway/     # API proxy core: routing, failover, concurrency
├── billing/     # Token billing, pricing, promo/redeem codes
├── channel/     # Channel routing, model pricing tiers
├── dashboard/   # Stats aggregation, trends, rankings
├── admin/       # Admin panel: settings, rules, scheduled tests
└── ops/         # Error monitoring, alerts, system metrics
```

Each module follows: `controller/ + service/ + mapper/ + model/(entity/ + vo/ + enums/)`

## Key Files to Read When Working On...

| Area | Files |
|------|-------|
| API Gateway flow | `gateway/service/ProxyService.java`, `gateway/controller/GatewayController.java` |
| Account scheduling | `account/service/AccountSelector.java`, `account/service/AccountService.java` |
| Authentication | `auth/filter/JwtAuthenticationFilter.java`, `auth/filter/ApiKeyAuthenticationFilter.java`, `common/config/SecurityConfig.java` |
| Billing | `billing/service/BillingService.java`, `billing/service/BillingCalculator.java`, `billing/service/PricingService.java` |
| OAuth | `auth/service/OAuthService.java`, `auth/service/platform/*.java`, `account/service/AccountRefreshService.java` |
| Failover | `gateway/service/FailoverService.java`, `gateway/service/FailoverState.java` |
| Concurrency | `gateway/service/ConcurrencyService.java` |
| Dashboard | `dashboard/service/DashboardService.java`, `dashboard/service/DashboardAggregationService.java` |
| Configuration | `common/config/*.java`, `application.yml` |

## Go-to-Java Mapping Reference

Understanding the Go codebase helps maintain API compatibility.

| Go Layer | Java Equivalent |
|----------|-----------------|
| `internal/handler/` | `module/*/controller/` |
| `internal/service/` | `module/*/service/` |
| `internal/repository/` | `module/*/mapper/` (MyBatis-Plus) |
| `ent/schema/` | `module/*/model/entity/` |
| `internal/handler/dto/` | `module/*/model/vo/` |
| `internal/config/config.go` | `application.yml` + `common/config/` |
| `internal/middleware/` | `auth/filter/` + `gateway/filter/` |
| `internal/server/routes/` | Controller `@RequestMapping` annotations |

| Go ORM (Ent) | Java ORM (MyBatis-Plus) |
|---------------|------------------------|
| `ent.Client` | `BaseMapper<T>` |
| `schema.Fields` | `@TableField` annotations |
| `schema.Edges` | Join queries in Mapper XML |
| `ent.Query().Where()` | `QueryWrapper<T>` / `LambdaQueryWrapper<T>` |
| `ent.Create()` | `mapper.insert()` |
| `ent.Update()` | `mapper.updateById()` |

---

## Git Commit Conventions

### Branch Strategy

```
main          <- stable, deployable
dev           <- integration branch
feat/*        <- new features
fix/*         <- bug fixes
refactor/*    <- refactoring
docs/*        <- documentation only
```

### Commit Message Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**Types:**

| Type | When to use |
|------|-------------|
| `feat` | New feature or service implementation |
| `fix` | Bug fix |
| `refactor` | Code refactoring (no behavior change) |
| `docs` | Documentation only |
| `style` | Formatting, missing semicolons (no logic change) |
| `test` | Adding or fixing tests |
| `chore` | Build, CI, dependencies |
| `perf` | Performance improvement |

**Scopes** (match module names):

`gateway`, `account`, `auth`, `billing`, `admin`, `user`, `apikey`, `channel`, `dashboard`, `ops`, `common`

**Examples:**

```
feat(gateway): implement WebSocket handler for OpenAI responses API
fix(account): fix selectByLowestUsage using proxyId instead of accountId
refactor(billing): extract CostBreakdown into separate class
docs: update PROGRESS.md with P2 service status
chore: upgrade Spring Boot to 3.4.1
```

### Commit Rules

1. One logical change per commit
2. Write commit messages in English (code comments can be Chinese)
3. Reference Go source file in commit body when porting: `Ported from: backend/internal/service/xxx.go`
4. Never commit: `.env`, `*.log`, `target/`, `.idea/`, `compile.log`

---

## Coding Standards

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Class | PascalCase | `AccountService`, `ProxyService` |
| Method | camelCase | `selectAccount()`, `refreshToken()` |
| Variable | camelCase | `accountId`, `refreshToken` |
| Constant | UPPER_SNAKE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| Package | lowercase | `com.sub2api.module.gateway` |
| Entity | Singular PascalCase | `Account`, `UsageLog` |
| Mapper | EntityName + Mapper | `AccountMapper`, `UsageLogMapper` |
| Service | Feature + Service | `AccountService`, `BillingCalculator` |
| Controller | Feature + Controller | `GatewayController`, `AuthController` |
| VO | Descriptive + suffix | `LoginRequest`, `DashboardStats` |
| Enum | PascalCase | `Platform`, `AccountStatus`, `ErrorCode` |

### Code Patterns

**Entity class pattern:**
```java
@Data
@TableName("table_name")
public class EntityName {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fieldName;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> jsonField;  // For JSONB columns

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;  // Soft delete
}
```

**Service class pattern:**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxMapper xxxMapper;
    private final StringRedisTemplate redisTemplate;

    // Redis key prefix convention
    private static final String CACHE_KEY_PREFIX = "sub2api:xxx:";
}
```

**Controller class pattern:**
```java
@RestController
@RequestMapping("/admin/xxx")  // or "/api/v1/xxx" for user-facing
@RequiredArgsConstructor
public class XxxController {
    private final XxxService xxxService;

    @GetMapping
    public Result<PageResult<Xxx>> list(...) { ... }

    @PostMapping
    public Result<Xxx> create(@RequestBody @Valid XxxRequest request) { ... }
}
```

**Unified response:**
```java
Result.ok(data)          // Success with data
Result.ok()              // Success without data
Result.fail(ErrorCode.XXX)        // Business error
Result.fail(ErrorCode.XXX, msg)   // Business error with message
```

### Redis Key Conventions

```
sub2api:session:{sessionId}           # Session data
sub2api:apikey:auth:{keyHash}         # API key auth cache
sub2api:billing:cache:{userId}        # Billing cache
sub2api:concurrency:account:{id}      # Account concurrency slots
sub2api:concurrency:user:{id}         # User concurrency slots
sub2api:rpm:{accountId}:{minute}      # RPM counter
sub2api:identity:{fingerprint}        # Identity cache
sub2api:setting:{key}                 # Setting cache
sub2api:pricing:models                # Model pricing cache
```

### Database Conventions

- Table names: `snake_case`, singular (e.g., `account`, `usage_log`)
- Column names: `snake_case` (MyBatis-Plus auto maps to camelCase)
- JSONB columns: Use `@TableField(typeHandler = JacksonTypeHandler.class)`
- Soft delete: `deleted_at` column (NULL = active, timestamp = deleted)
- Timestamps: `created_at`, `updated_at` with auto-fill via MybatisPlusConfig
- Group table: `app_group` (not `group`, which is a SQL reserved word)

### Error Handling

- Business errors: Throw `BusinessException(ErrorCode.XXX)`
- Rate limit errors: Throw `RateLimitException(message)`
- All exceptions caught by `GlobalExceptionHandler`
- Error codes: See `common/model/enums/ErrorCode.java` (organized by module: 1xxx general, 2xxx auth, 3xxx user, 4xxx account, 5xxx gateway, 6xxx billing, 7xxx admin, 8xxx external)

---

## Architecture Constraints

1. **API compatibility**: All endpoints must match the Go backend's routes exactly. The Vue 3 frontend connects to the same API paths.
2. **Database schema**: Use the existing PostgreSQL schema created by Go backend migrations (`backend/migrations/`). Do NOT create new migration files in Java.
3. **No Flyway/Liquibase**: Database migrations are managed by the Go backend. Java backend is a drop-in replacement.
4. **Redis sharing**: Redis keys and data formats must be compatible with the Go backend for seamless switchover.
5. **Soft delete**: All entities with `deletedAt` use logical delete. Never hard-delete user data.

---

## Development Workflow

### Before modifying any service:

1. Read the corresponding Go source file first: `backend/internal/service/xxx.go`
2. Check if there's a related Go handler: `backend/internal/handler/xxx.go`
3. Understand the data flow: handler -> service -> repository
4. Check existing Java implementation and tests

### After modifying code:

1. Compile check: `mvn compile`
2. Run related tests (when available)
3. Update PROGRESS.md if completing a pending item
4. Commit with proper format (see Git Conventions above)

### Adding a new service:

1. Identify the correct module (`account`, `gateway`, `billing`, etc.)
2. Create entity in `module/xxx/model/entity/` if needed
3. Create mapper in `module/xxx/mapper/`
4. Create service in `module/xxx/service/`
5. Create controller in `module/xxx/controller/` if exposing API
6. Add mapper XML in `resources/mapper/` if custom SQL needed
7. Update PROGRESS.md

---

## Known Issues / Gotchas

1. **pom.xml Java version**: Compiler target is 17 in pom.xml but runtime uses JDK 21. This is intentional for broader compatibility.
2. **MapperScan scope**: `@MapperScan("com.sub2api.module.*.mapper")` - only scans direct subpackage mappers. Nested packages (e.g., `admin.mapper`) require the wildcard to be `com.sub2api.module.**.mapper` or explicit listing.
3. **JSONB handling**: PostgreSQL JSONB columns must use `@TableField(typeHandler = JacksonTypeHandler.class)` in entities.
4. **Group table name**: The `groups` table is mapped as `app_group` in Java because `group` is a SQL reserved keyword. The actual DB table name may differ - check the Go migrations.
5. **Soft delete**: MyBatis-Plus logic delete is configured via `deletedAt` field in `application.yml`. Queries automatically filter soft-deleted records.
6. **OAuth secrets**: Never hardcode. Always use environment variables: `${ANTHROPIC_CLIENT_SECRET}`, `${OPENAI_CLIENT_SECRET}`, etc.
7. **Windows development**: Use `127.0.0.1` instead of `localhost` for PostgreSQL connections (IPv6 issues). Use Git Bash, not PowerShell, for shell commands.

---

## File Index (Quick Navigation)

| Category | Path |
|----------|------|
| Project docs | `README.md`, `ARCHITECTURE.md`, `PROGRESS.md` |
| Build config | `pom.xml`, `Dockerfile`, `docker-compose.yml` |
| App config | `src/main/resources/application.yml` |
| Security config | `module/common/config/SecurityConfig.java` |
| Main entry | `Sub2ApiApplication.java` |
| Error codes | `module/common/model/enums/ErrorCode.java` |
| API response | `module/common/model/vo/Result.java` |
| Exception handler | `module/common/exception/GlobalExceptionHandler.java` |
| MyBatis mappers | `src/main/resources/mapper/*.xml` |
| Code style | `checkstyle/checkstyle.xml` |
