package com.sub2api.module.gateway.controller;

import com.sub2api.module.gateway.service.ProxyService;
import com.sub2api.module.gateway.service.ProxyService.ProxyRequest;
import com.sub2api.module.common.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * OpenAI 兼容 API 控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "OpenAI", description = "OpenAI API 兼容接口")
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAIController {

    private final ProxyService proxyService;

    @Operation(summary = "聊天完成")
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatCompletionsStream(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(300000L);

        try {
            String clientIp = IpUtil.getRealIp(request);
            ProxyRequest proxyRequest = new ProxyRequest();
            proxyRequest.setPlatform("openai");
            proxyRequest.setPath("/v1/chat/completions");
            proxyRequest.setBody(body);
            proxyRequest.setClientIp(clientIp);
            proxyRequest.setStream(true);
            proxyRequest.setSessionId(request.getHeader("X-Session-Id"));

            proxyService.proxyStreamRequest(proxyRequest)
                    .doOnNext(chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(e -> {
                        log.error("流式响应异常: {}", e.getMessage());
                        emitter.completeWithError(e);
                    })
                    .doOnComplete(emitter::complete)
                    .subscribe();

        } catch (Exception e) {
            log.error("处理聊天完成请求失败: {}", e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Operation(summary = "聊天完成 (非流式)")
    @PostMapping(value = "/chat/completions_sync")
    public ResponseEntity<Map<String, Object>> chatCompletionsSync(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String clientIp = IpUtil.getRealIp(request);
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("openai");
        proxyRequest.setPath("/v1/chat/completions");
        proxyRequest.setBody(body);
        proxyRequest.setClientIp(clientIp);
        proxyRequest.setStream(false);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "模型列表")
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("openai");
        proxyRequest.setPath("/v1/models");
        proxyRequest.setBody(Map.of());

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Embeddings")
    @PostMapping("/embeddings")
    public ResponseEntity<Map<String, Object>> embeddings(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String clientIp = IpUtil.getRealIp(request);
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("openai");
        proxyRequest.setPath("/v1/embeddings");
        proxyRequest.setBody(body);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }
}
