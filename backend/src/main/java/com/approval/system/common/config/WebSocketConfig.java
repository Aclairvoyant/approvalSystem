package com.approval.system.common.config;

import com.approval.system.websocket.WebSocketHandshakeInterceptor;
import com.approval.system.service.IGobangService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;

/**
 * WebSocket配置类
 * 用于配置STOMP消息代理和WebSocket端点
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String GOBANG_TOPIC_PREFIX = "/topic/gobang/";

    @Autowired
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    @Autowired
    private IGobangService gobangService;

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

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    validateGobangSubscription(accessor);
                }
                return message;
            }
        });
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

    private void validateGobangSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(GOBANG_TOPIC_PREFIX)) {
            return;
        }

        Long gameId = parseGameId(destination.substring(GOBANG_TOPIC_PREFIX.length()));
        Long userId = getUserId(accessor.getSessionAttributes());
        if (gameId == null || userId == null || !gobangService.validateGamePlayer(gameId, userId)) {
            throw new AccessDeniedException("没有权限订阅该五子棋房间");
        }
    }

    private Long parseGameId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getUserId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        Object userId = sessionAttributes.get("userId");
        if (userId instanceof Long longValue) {
            return longValue;
        }
        if (userId instanceof Integer intValue) {
            return intValue.longValue();
        }
        return null;
    }
}
