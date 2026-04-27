package com.sub2api.module.auth.controller;

import com.sub2api.module.auth.service.OAuthService;
import com.sub2api.module.common.model.vo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OAuth 控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "OAuth", description = "第三方 OAuth 认证接口")
@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    @Operation(summary = "获取支持的 OAuth 平台列表")
    @GetMapping("/providers")
    public Result<List<Map<String, String>>> getOAuthProviders() {
        var providers = List.of(
                Map.of("id", "anthropic", "name", "Anthropic", "icon", "anthropic.svg"),
                Map.of("id", "openai", "name", "OpenAI", "icon", "openai.svg"),
                Map.of("id", "google", "name", "Google", "icon", "google.svg"),
                Map.of("id", "linuxdo", "name", "Linux.do", "icon", "linuxdo.svg")
        );
        return Result.ok(providers);
    }
}
