package com.sub2api.module.admin.controller;

import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "健康检查", description = "系统健康状态接口")
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "健康检查")
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        // 检查数据库
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", conn.isValid(1) ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("databaseError", e.getMessage());
        }

        // 检查 Redis
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            health.put("redis", "PONG".equals(pong) ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("redis", "DOWN");
            health.put("redisError", e.getMessage());
        }

        return Result.ok(health);
    }

    @Operation(summary = "就绪检查")
    @GetMapping("/ready")
    public Result<Void> ready() {
        return Result.ok();
    }

    @Operation(summary = "存活检查")
    @GetMapping("/live")
    public Result<Void> live() {
        return Result.ok();
    }
}
