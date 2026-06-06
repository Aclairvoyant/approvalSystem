package com.approval.system.websocket;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.dto.GobangResponse;
import com.approval.system.service.IGobangService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
public class GobangWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private IGobangService gobangService;

    @MessageMapping("/gobang/{gameId}/place-stone")
    public void placeStone(@DestinationVariable Long gameId,
                           @Payload Map<String, Object> payload,
                           SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);
        Integer row = readInt(payload, "row");
        Integer col = readInt(payload, "col");

        try {
            GobangResponse response = gobangService.placeStone(gameId, userId, row, col);
            broadcast(gameId, GobangMessage.stonePlaced(gameId, userId, username, row, col, response));
            if (GameStatusEnum.FINISHED.getCode().equals(response.getGameStatus())) {
                broadcast(gameId, GobangMessage.gameEnded(gameId, response));
            }
        } catch (Exception e) {
            log.error("五子棋落子失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    @MessageMapping("/gobang/{gameId}/undo/request")
    public void requestUndo(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);
        try {
            GobangResponse response = gobangService.requestUndo(gameId, userId);
            broadcast(gameId, GobangMessage.undoRequested(gameId, userId, username, response));
        } catch (Exception e) {
            log.error("五子棋悔棋请求失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    @MessageMapping("/gobang/{gameId}/undo/respond")
    public void respondUndo(@DestinationVariable Long gameId,
                            @Payload Map<String, Object> payload,
                            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);
        Boolean accepted = readBoolean(payload, "accepted");
        try {
            GobangResponse response = gobangService.respondUndo(gameId, userId, accepted);
            broadcast(gameId, GobangMessage.undoResolved(gameId, userId, username, accepted, response));
        } catch (Exception e) {
            log.error("五子棋悔棋响应失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    @MessageMapping("/gobang/{gameId}/surrender")
    public void surrender(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);
        try {
            GobangResponse response = gobangService.surrender(gameId, userId);
            broadcast(gameId, GobangMessage.surrendered(gameId, userId, username, response));
            broadcast(gameId, GobangMessage.gameEnded(gameId, response));
        } catch (Exception e) {
            log.error("五子棋认输失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    @MessageMapping("/gobang/{gameId}/sync")
    public void sync(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);
        try {
            GobangResponse response = gobangService.getGameDetail(gameId, userId);
            broadcast(gameId, GobangMessage.stateUpdate(gameId, userId, username, response));
        } catch (Exception e) {
            log.error("五子棋同步失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    @MessageMapping("/gobang/{gameId}/heartbeat")
    public void heartbeat(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        try {
            if (gobangService.validateGamePlayer(gameId, userId)) {
                gobangService.updateLastMoveTime(gameId);
            }
        } catch (Exception e) {
            log.debug("五子棋心跳失败: gameId={}, userId={}", gameId, userId);
        }
    }

    private void broadcast(Long gameId, GobangMessage message) {
        messagingTemplate.convertAndSend("/topic/gobang/" + gameId, message);
    }

    private void sendError(Long gameId, String errorMessage) {
        broadcast(gameId, GobangMessage.error(gameId, errorMessage));
    }

    private Integer readInt(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new RuntimeException("参数 " + key + " 无效");
    }

    private Boolean readBoolean(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new RuntimeException("参数 " + key + " 无效");
    }

    private Long getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userId = sessionAttributes.get("userId");
            if (userId instanceof Long longValue) {
                return longValue;
            }
            if (userId instanceof Integer intValue) {
                return intValue.longValue();
            }
        }
        return null;
    }

    private String getUsernameFromSession(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            return (String) sessionAttributes.get("username");
        }
        return null;
    }
}
