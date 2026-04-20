package com.sub2api.module.common.config;

import com.sub2api.module.auth.filter.ApiKeyAuthenticationFilter;
import com.sub2api.module.auth.filter.JwtAuthenticationFilter;
import com.sub2api.module.common.model.enums.ErrorCode;
import com.sub2api.module.common.model.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置
 *
 * @author Alibaba Java Code Guidelines
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 启用 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 禁用 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 禁用 Form 登录
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用 HTTP Basic
                .httpBasic(AbstractHttpConfigurer::disable)
                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        // 公开接口
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/setup/**").permitAll()
                        .requestMatchers("/settings/public").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        // API Key 可访问的接口
                        .requestMatchers("/v1/**").permitAll()
                        .requestMatchers("/v1beta/**").permitAll()
                        .requestMatchers("/antigravity/**").permitAll()
                        // Swagger
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 静态资源
                        .requestMatchers(HttpMethod.GET, "/*.html", "/html/**", "/css/**", "/js/**").permitAll()
                        // 管理接口需要 ADMIN 角色
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 添加 API Key 过滤器
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 认证异常处理
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    /**
     * 认证失败时返回 JSON 错误响应
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = new Result<>();
            result.setSuccess(false);
            result.setCode(ErrorCode.UNAUTHORIZED.getCode());
            result.setMessage("未登录或登录已过期");
            response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
        };
    }

    /**
     * 权限不足时返回 JSON 错误响应
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result<Void> result = new Result<>();
            result.setSuccess(false);
            result.setCode(ErrorCode.FORBIDDEN.getCode());
            result.setMessage("权限不足");
            response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
        };
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Request-Id", "X-Response-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
