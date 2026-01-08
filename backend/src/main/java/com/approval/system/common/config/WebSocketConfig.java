package com.approval.system.common.config;

import com.approval.system.websocket.WebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 用于配置STOMP消息代理和WebSocket端点
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    /**
     * 配置消息代理
     * @param config 消息代理注册表
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于向客户端发送消息
        // /topic 用于广播消息（一对多）
        // /queue 用于点对点消息（一对一）
        config.enableSimpleBroker("/topic", "/queue");

        // 设置客户端发送消息的前缀
        // 客户端发送的消息如果以/app开头，会被路由到@MessageMapping注解的方法
        config.setApplicationDestinationPrefixes("/app");

        // 设置用户订阅前缀
        // 用于点对点消息推送
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 注册STOMP端点
     * @param registry STOMP端点注册表
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，供客户端连接
        // /ws/game 是WebSocket连接的URL
        registry.addEndpoint("/ws/game")
                .setAllowedOriginPatterns("*")  // 允许所有源（生产环境应该限制具体域名）
                .addInterceptors(handshakeInterceptor)  // 添加JWT验证拦截器
                .withSockJS();  // 启用SockJS降级选项，支持不支持WebSocket的浏览器
    }
}
