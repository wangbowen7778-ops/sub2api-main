FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# 复制 Maven 相关文件
COPY pom.xml .
COPY settings.xml .

# 下载依赖 (利用缓存)
RUN mvn dependency:go-offline -B

# 复制源码
COPY src ./src

# 构建
RUN mvn clean package -DskipTests -B

# 运行镜像
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache bash curl tzdata

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# 复制构建产物
COPY --from=builder /app/target/sub2api-backend.jar app.jar

# 复制前端静态资源 (可选)
# COPY --from=builder /app/frontend/dist /app/public

# 创建非 root 用户
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup
USER appuser

# 环境变量
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
