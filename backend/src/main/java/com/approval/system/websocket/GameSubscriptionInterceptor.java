package com.approval.system.websocket;

import com.approval.system.service.IGobangService;
import com.approval.system.service.IMahjongService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP 入站拦截：
 * <ul>
 *   <li>CONNECT：把握手时放进 session 的 userId 绑定成连接的 Principal，
 *       这样服务端可以用 convertAndSendToUser 只发给这个用户（麻将手牌走这条路）；</li>
 *   <li>SUBSCRIBE：五子棋房间只允许参与者订阅；麻将的房间 topic 和个人队列只允许坐在这桌的人订阅，
 *       其他带 mahjong 的地址（比如直接订阅解析后的 /queue/mahjong/x-userXXX）一律拒绝。</li>
 * </ul>
 */
public class GameSubscriptionInterceptor implements ChannelInterceptor {

    private static final String GOBANG_TOPIC_PREFIX = "/topic/gobang/";
    private static final String MAHJONG_TOPIC_PREFIX = "/topic/mahjong/";
    private static final String MAHJONG_USER_QUEUE_PREFIX = "/user/queue/mahjong/";

    private final IGobangService gobangService;
    private final IMahjongService mahjongService;

    public GameSubscriptionInterceptor(IGobangService gobangService, IMahjongService mahjongService) {
        this.gobangService = gobangService;
        this.mahjongService = mahjongService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            bindUser(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateGobangSubscription(accessor);
            validateMahjongSubscription(accessor);
        }
        return message;
    }

    private void bindUser(StompHeaderAccessor accessor) {
        Long userId = getUserId(accessor.getSessionAttributes());
        if (accessor.getUser() == null && userId != null) {
            accessor.setUser(new StompUserPrincipal(userId));
        }
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

    private void validateMahjongSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.contains("mahjong")) {
            return;
        }
        Long gameId = null;
        if (destination.startsWith(MAHJONG_TOPIC_PREFIX)) {
            gameId = parseGameId(destination.substring(MAHJONG_TOPIC_PREFIX.length()));
        } else if (destination.startsWith(MAHJONG_USER_QUEUE_PREFIX)) {
            gameId = parseGameId(destination.substring(MAHJONG_USER_QUEUE_PREFIX.length()));
        }
        Long userId = getUserId(accessor.getSessionAttributes());
        if (gameId == null || userId == null || !mahjongService.isSeated(gameId, userId)) {
            throw new AccessDeniedException("没有权限订阅该麻将房间");
        }
    }

    private static Long parseGameId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long getUserId(Map<String, Object> sessionAttributes) {
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

    /** STOMP 连接的用户：名字就是用户 ID，convertAndSendToUser 按它路由 */
    public record StompUserPrincipal(Long userId) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
