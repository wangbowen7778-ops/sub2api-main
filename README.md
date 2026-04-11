# Sub2API Java Backend

AI API Gateway 后端服务 (Java 版)

## 技术栈

- Java 21
- Spring Boot 3.4
- MyBatis-Plus 3.5
- PostgreSQL 15+
- Redis 7+
- Spring Security + JWT

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+

### 配置

1. 复制配置模板:
```bash
cp src/main/resources/application.yml src/main/resources/application-local.yml
```

2. 修改 `application-local.yml` 中的数据库和 Redis 连接信息

### 构建

```bash
mvn clean package -DskipTests
```

### 运行

```bash
java -jar target/sub2api-backend.jar
```

### Docker 部署

```bash
docker-compose up -d
```

## 项目结构

```
src/main/java/com/sub2api/
├── module/
│   ├── common/          # 通用模块 (配置/异常/工具)
│   ├── auth/           # 认证模块 (JWT/OAuth/API Key)
│   ├── user/           # 用户模块
│   ├── apikey/         # API Key 模块
│   ├── account/        # 账号模块
│   ├── billing/        # 计费模块
│   ├── gateway/        # API 网关模块
│   └── admin/          # 管理后台模块
└── resources/
    └── mapper/         # MyBatis XML
```

## API 文档

启动后访问: http://localhost:8080/swagger-ui.html

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| DB_HOST | 数据库主机 | localhost |
| DB_PORT | 数据库端口 | 5432 |
| DB_NAME | 数据库名 | sub2api |
| DB_USERNAME | 数据库用户名 | postgres |
| DB_PASSWORD | 数据库密码 | postgres |
| REDIS_HOST | Redis 主机 | localhost |
| REDIS_PORT | Redis 端口 | 6379 |
| JWT_SECRET | JWT 密钥 | (需设置) |
| OAUTH_* | OAuth 配置 | (可选) |

## 开发规范

- 遵循阿里巴巴 Java 开发规范
- 使用 Lombok 简化代码
- 所有接口返回统一响应格式 (Result)
- 异常通过 GlobalExceptionHandler 统一处理
