package com.sub2api.module.gateway.controller;

import com.sub2api.module.gateway.service.ProxyService;
import com.sub2api.module.gateway.service.ProxyService.ProxyRequest;
import com.sub2api.module.common.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Antigravity API 控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "Antigravity", description = "Antigravity API 兼容接口")
@Slf4j
@RestController
@RequestMapping("/antigravity")
@RequiredArgsConstructor
public class AntigravityController {

    private final ProxyService proxyService;

    @Operation(summary = "Antigravity 聊天完成")
    @PostMapping("/v1/chat/completions")
    public ResponseEntity<Map<String, Object>> chatCompletions(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String clientIp = IpUtil.getRealIp(request);
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("antigravity");
        proxyRequest.setPath("/antigravity/v1/chat/completions");
        proxyRequest.setBody(body);
        proxyRequest.setClientIp(clientIp);
        proxyRequest.setSessionId(request.getHeader("X-Session-Id"));

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Antigravity 模型列表")
    @GetMapping("/v1/models")
    public ResponseEntity<Map<String, Object>> models(HttpServletRequest request) {
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("antigravity");
        proxyRequest.setPath("/antigravity/v1/models");
        proxyRequest.setBody(Map.of());

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }
}
