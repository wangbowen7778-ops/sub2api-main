package com.sub2api.module.auth.filter;

import com.sub2api.module.auth.service.JwtService;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 如果已经认证过，直接放行
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.validateAccessToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            // 获取用户并检查状态
            User user = userService.findByUsername(username);
            if (user == null) {
                filterChain.doFilter(request, response);
                return;
            }
            userService.checkUserStatus(user);

            // 创建认证令牌
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
            );
            authentication.setDetails(request);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 认证成功: username={}", username);
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("JWT 无效: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 公开路径不需要过滤
        return path.startsWith("/auth/") ||
                path.startsWith("/v1/") ||
                path.startsWith("/v1beta/") ||
                path.startsWith("/antigravity/") ||
                path.equals("/health") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/api-docs");
    }
}
