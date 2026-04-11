package com.sub2api.module.common.config;

import com.sub2api.module.auth.websocket.AuthHandshakeInterceptor;
import com.sub2api.module.gateway.websocket.OpenAIWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 *
 * @author Alibaba Java Code Guidelines
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final OpenAIWebSocketHandler openAIWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置消息代理前缀
        // /topic 用于广播消息
        // /queue 用于点对点消息
        registry.enableSimpleBroker("/topic", "/queue");
        // 配置应用目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 配置用户目的地前缀
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 OpenAI WebSocket 端点
        registry.addHandler(openAIWebSocketHandler, "/v1/responses/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor);
    }
}
