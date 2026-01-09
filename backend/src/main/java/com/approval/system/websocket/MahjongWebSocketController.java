package com.approval.system.websocket;

import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongGameResponse;
import com.approval.system.service.IMahjongService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 麻将游戏WebSocket控制器
 * 处理麻将游戏中的实时消息
 */
@Slf4j
@Controller
public class MahjongWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private IMahjongService mahjongService;

    /**
     * 加入游戏房间（建立WebSocket连接后调用）
     * 客户端发送到: /app/mahjong/{gameId}/join
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/join")
    public void joinRoom(@DestinationVariable Long gameId,
                         SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("玩家加入麻将房间: gameId={}, userId={}", gameId, userId);

        try {
            MahjongGameResponse gameState = mahjongService.getGameState(gameId, userId);

            // 广播玩家加入消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "PLAYER_JOINED");
            message.put("gameId", gameId);
            message.put("playerId", userId);
            message.put("playerName", username);
            message.put("gameState", gameState);

            broadcastToGame(gameId, message);

        } catch (Exception e) {
            log.error("加入房间失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 准备开始游戏
     * 客户端发送到: /app/mahjong/{gameId}/ready
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/ready")
    public void ready(@DestinationVariable Long gameId,
                      SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("玩家准备: gameId={}, userId={}", gameId, userId);

        try {
            // 广播准备消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "PLAYER_READY");
            message.put("gameId", gameId);
            message.put("playerId", userId);
            message.put("playerName", username);

            broadcastToGame(gameId, message);

        } catch (Exception e) {
            log.error("准备失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 开始游戏
     * 客户端发送到: /app/mahjong/{gameId}/start
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/start")
    public void startGame(@DestinationVariable Long gameId,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("开始游戏请求: gameId={}, userId={}", gameId, userId);

        try {
            MahjongGameResponse gameState = mahjongService.startGame(gameId, userId);

            // 广播游戏开始消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_STARTED");
            message.put("gameId", gameId);
            message.put("gameState", gameState);

            broadcastToGame(gameId, message);

            // 向每个玩家发送其私有信息（手牌）
            sendPrivateGameState(gameId);

        } catch (Exception e) {
            log.error("开始游戏失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 执行游戏操作（出牌、碰、杠、胡等）
     * 客户端发送到: /app/mahjong/{gameId}/action
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/action")
    public void executeAction(@DestinationVariable Long gameId,
                              @Payload MahjongActionRequest request,
                              SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("执行操作请求: gameId={}, userId={}, action={}, tile={}",
                gameId, userId, request.getActionType(), request.getTile());

        try {
            MahjongGameResponse gameState = mahjongService.executeAction(gameId, userId, request);

            // 广播操作消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "ACTION_EXECUTED");
            message.put("gameId", gameId);
            message.put("playerId", userId);
            message.put("playerName", username);
            message.put("actionType", request.getActionType());
            message.put("tile", request.getTile());
            message.put("seat", mahjongService.getPlayerSeat(gameId, userId));

            broadcastToGame(gameId, message);

            // 发送更新后的游戏状态
            sendGameStateUpdate(gameId);

            // 向每个玩家发送其私有信息
            sendPrivateGameState(gameId);

        } catch (Exception e) {
            log.error("执行操作失败: gameId={}, userId={}, action={}",
                    gameId, userId, request.getActionType(), e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 出牌
     * 客户端发送到: /app/mahjong/{gameId}/discard
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/discard")
    public void discard(@DestinationVariable Long gameId,
                        @Payload Map<String, String> payload,
                        SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String tile = payload.get("tile");

        MahjongActionRequest request = new MahjongActionRequest();
        request.setActionType("DISCARD");
        request.setTile(tile);

        executeAction(gameId, request, headerAccessor);
    }

    /**
     * 碰牌
     * 客户端发送到: /app/mahjong/{gameId}/pong
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/pong")
    public void pong(@DestinationVariable Long gameId,
                     @Payload Map<String, String> payload,
                     SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String tile = payload.get("tile");

        MahjongActionRequest request = new MahjongActionRequest();
        request.setActionType("PONG");
        request.setTile(tile);

        executeAction(gameId, request, headerAccessor);
    }

    /**
     * 杠牌
     * 客户端发送到: /app/mahjong/{gameId}/kong
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/kong")
    public void kong(@DestinationVariable Long gameId,
                     @Payload Map<String, String> payload,
                     SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String tile = payload.get("tile");
        String kongType = payload.getOrDefault("kongType", "MING_KONG");

        MahjongActionRequest request = new MahjongActionRequest();
        request.setActionType(kongType);
        request.setTile(tile);

        executeAction(gameId, request, headerAccessor);
    }

    /**
     * 胡牌
     * 客户端发送到: /app/mahjong/{gameId}/hu
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/hu")
    public void hu(@DestinationVariable Long gameId,
                   SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);

        MahjongActionRequest request = new MahjongActionRequest();
        request.setActionType("HU");

        executeAction(gameId, request, headerAccessor);
    }

    /**
     * 过（跳过碰/杠/胡）
     * 客户端发送到: /app/mahjong/{gameId}/pass
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/pass")
    public void pass(@DestinationVariable Long gameId,
                     SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);

        MahjongActionRequest request = new MahjongActionRequest();
        request.setActionType("PASS");

        executeAction(gameId, request, headerAccessor);
    }

    /**
     * 同步游戏状态
     * 客户端发送到: /app/mahjong/{gameId}/sync
     * 响应到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/sync")
    public void syncGameState(@DestinationVariable Long gameId,
                              SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);

        log.info("同步麻将游戏状态请求: gameId={}, userId={}", gameId, userId);

        try {
            sendGameStateUpdate(gameId);
            sendPrivateGameState(gameId);
        } catch (Exception e) {
            log.error("同步游戏状态失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 开始下一局
     * 客户端发送到: /app/mahjong/{gameId}/next-round
     * 广播到: /topic/mahjong/game/{gameId}
     */
    @MessageMapping("/mahjong/{gameId}/next-round")
    public void nextRound(@DestinationVariable Long gameId,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);

        log.info("开始下一局请求: gameId={}, userId={}", gameId, userId);

        try {
            MahjongGameResponse gameState = mahjongService.nextRound(gameId, userId);

            // 广播新局开始消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "ROUND_STARTED");
            message.put("gameId", gameId);
            message.put("roundNumber", gameState.getCurrentRound());
            message.put("dealerSeat", gameState.getDealerSeat());

            broadcastToGame(gameId, message);

            // 向每个玩家发送其私有信息
            sendPrivateGameState(gameId);

        } catch (Exception e) {
            log.error("开始下一局失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, userId, e.getMessage());
        }
    }

    /**
     * 心跳
     * 客户端发送到: /app/mahjong/{gameId}/heartbeat
     */
    @MessageMapping("/mahjong/{gameId}/heartbeat")
    public void heartbeat(@DestinationVariable Long gameId,
                          SimpMessageHeaderAccessor headerAccessor) {
        // 简单心跳，保持连接
        log.debug("麻将心跳: gameId={}", gameId);
    }

    // ==================== 私有方法 ====================

    /**
     * 广播消息到游戏房间
     */
    private void broadcastToGame(Long gameId, Map<String, Object> message) {
        messagingTemplate.convertAndSend("/topic/mahjong/game/" + gameId, message);
    }

    /**
     * 发送游戏状态更新（公开信息）
     */
    private void sendGameStateUpdate(Long gameId) {
        try {
            // 获取游戏状态（不包含私有信息）
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_STATE_UPDATE");
            message.put("gameId", gameId);

            broadcastToGame(gameId, message);
        } catch (Exception e) {
            log.error("发送游戏状态更新失败: gameId={}", gameId, e);
        }
    }

    /**
     * 向每个玩家发送其私有游戏状态（手牌等）
     */
    private void sendPrivateGameState(Long gameId) {
        try {
            // 获取游戏信息以确定玩家
            // 由于游戏状态包含敏感信息（手牌），需要分别发送给每个玩家

            // 向游戏频道广播完整游戏状态（但每个玩家只能看到自己的手牌）
            // 实际的手牌隔离是在 buildGameResponse 中根据 userId 实现的
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_STATE_UPDATE");
            message.put("gameId", gameId);

            // 通知客户端刷新游戏状态（客户端会通过REST API获取最新状态）
            broadcastToGame(gameId, message);

            log.debug("已发送游戏状态更新通知: gameId={}", gameId);
        } catch (Exception e) {
            log.error("发送私有游戏状态失败: gameId={}", gameId, e);
        }
    }

    /**
     * 发送错误消息给特定用户
     */
    private void sendError(Long gameId, Long userId, String errorMessage) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ERROR");
        message.put("gameId", gameId);
        message.put("error", errorMessage);

        // 广播错误（在实际应用中应该只发给特定用户）
        broadcastToGame(gameId, message);
    }

    /**
     * 从session获取用户ID
     */
    private Long getUserIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userId = sessionAttributes.get("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            } else if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
        }
        return null;
    }

    /**
     * 从session获取用户名
     */
    private String getUsernameFromSession(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            return (String) sessionAttributes.get("username");
        }
        return null;
    }
}
