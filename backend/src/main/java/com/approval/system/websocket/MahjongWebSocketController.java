package com.approval.system.websocket;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.service.IMahjongService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 麻将的 STOMP 入口：/app/mahjong/{gameId}/action、/sync、/heartbeat。
 * 操作结果由服务层在事务提交后推送；出错只回给操作者本人，不广播到房间。
 */
@Slf4j
@Controller
public class MahjongWebSocketController {

    private static final String GENERIC_ERROR = "操作失败，请稍后重试";

    private final IMahjongService mahjongService;
    private final SimpMessagingTemplate messagingTemplate;

    public MahjongWebSocketController(IMahjongService mahjongService, SimpMessagingTemplate messagingTemplate) {
        this.mahjongService = mahjongService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/mahjong/{gameId}/action")
    public void action(@DestinationVariable Long gameId,
                       @Valid @Payload MahjongActionRequest request,
                       SimpMessageHeaderAccessor headerAccessor) {
        Long userId = requireUser(headerAccessor);
        mahjongService.act(gameId, userId, request);
    }

    /** 订阅后或重连后拉一次完整的个人视角（公共信息 + 自己的手牌） */
    @MessageMapping("/mahjong/{gameId}/sync")
    public void sync(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = requireUser(headerAccessor);
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), MahjongMessage.userQueue(gameId),
                MahjongMessage.sync(gameId, mahjongService.getRoom(gameId, userId)));
    }

    @MessageMapping("/mahjong/{gameId}/heartbeat")
    public void heartbeat(@DestinationVariable Long gameId) {
        // 只用来保持连接活跃；断线判断交给超时托管
    }

    @MessageExceptionHandler
    public void handleException(Exception exception, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserId(headerAccessor);
        Long gameId = parseGameId(headerAccessor.getDestination());
        String message;
        if (exception instanceof BusinessException businessException) {
            message = businessException.getMessage();
        } else if (exception instanceof MethodArgumentNotValidException invalid
                && invalid.getBindingResult() != null
                && invalid.getBindingResult().getFieldError() != null) {
            message = invalid.getBindingResult().getFieldError().getDefaultMessage();
        } else {
            log.error("麻将操作失败: gameId={}, userId={}", gameId, userId, exception);
            message = GENERIC_ERROR;
        }
        if (userId != null && gameId != null) {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), MahjongMessage.userQueue(gameId),
                    MahjongMessage.error(gameId, message));
        }
    }

    private Long requireUser(SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserId(headerAccessor);
        if (userId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        return userId;
    }

    static Long getUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object userId = attributes.get("userId");
        if (userId instanceof Long longValue) {
            return longValue;
        }
        if (userId instanceof Integer intValue) {
            return intValue.longValue();
        }
        return null;
    }

    /** /app/mahjong/{gameId}/... → gameId */
    static Long parseGameId(String destination) {
        if (destination == null) {
            return null;
        }
        String[] parts = destination.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("mahjong".equals(parts[i])) {
                try {
                    return Long.valueOf(parts[i + 1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
