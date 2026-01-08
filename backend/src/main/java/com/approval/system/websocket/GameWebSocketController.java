package com.approval.system.websocket;

import com.approval.system.dto.GameResponse;
import com.approval.system.dto.GameTaskResponse;
import com.approval.system.entity.Game;
import com.approval.system.entity.GameTaskRecord;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IFlightChessEngine;
import com.approval.system.service.IGameService;
import com.approval.system.service.IGameTaskRecordService;
import com.approval.system.service.IGameTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * 游戏WebSocket控制器
 * 处理游戏中的实时消息
 */
@Slf4j
@Controller
public class GameWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private IGameService gameService;

    @Autowired
    private IGameTaskService gameTaskService;

    @Autowired
    private IGameTaskRecordService gameTaskRecordService;

    @Autowired
    private IFlightChessEngine chessEngine;

    @Autowired
    private UserMapper userMapper;

    /**
     * 掷骰子
     * 客户端发送到: /app/game/{gameId}/roll-dice
     * 广播到: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/roll-dice")
    public void rollDice(@DestinationVariable Long gameId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("掷骰子请求: gameId={}, userId={}", gameId, userId);

        try {
            // 验证玩家权限
            if (!gameService.validateGamePlayer(gameId, userId)) {
                sendError(gameId, "您不是这场游戏的参与者");
                return;
            }

            // 验证轮次
            if (!gameService.isPlayerTurn(gameId, userId)) {
                sendError(gameId, "还没轮到您");
                return;
            }

            // 掷骰子
            Integer diceResult = gameService.rollDice(gameId, userId);

            // 广播骰子结果（先广播让前端显示动画）
            GameMessage message = GameMessage.diceRolled(gameId, userId, username, diceResult);
            broadcastToGame(gameId, message);

            // 检查是否有可移动的棋子
            Game game = gameService.getGameById(gameId);
            int playerNumber = game.getPlayer1Id().equals(userId) ? 1 : 2;
            List<Integer> playerPieces = playerNumber == 1 ? game.getPlayer1Pieces() : game.getPlayer2Pieces();

            if (!chessEngine.hasMovablePiece(playerPieces, diceResult)) {
                log.info("玩家无可移动棋子，自动跳过回合: gameId={}, userId={}, diceResult={}", gameId, userId, diceResult);

                // 延迟1.5秒后再广播跳过回合消息，让玩家看到骰子结果
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 广播跳过回合消息
                GameMessage skipMessage = GameMessage.builder()
                        .type(GameMessage.MessageType.ERROR)
                        .gameId(gameId)
                        .senderId(userId)
                        .senderName(username)
                        .data(Map.of("error", "无可移动棋子，自动跳过回合"))
                        .build();
                broadcastToGame(gameId, skipMessage);

                // 切换回合（掷到6也要切换，因为没有可移动的棋子）
                int newTurn = game.getCurrentTurn() == 1 ? 2 : 1;
                game.setCurrentTurn(newTurn);
                game.setLastDiceResult(null);  // 清空骰子结果
                gameService.updateById(game);

                // 广播轮次切换
                Long nextPlayerId = newTurn == 1 ? game.getPlayer1Id() : game.getPlayer2Id();
                GameMessage turnMessage = GameMessage.turnChanged(gameId, newTurn, nextPlayerId);
                broadcastToGame(gameId, turnMessage);
            }

            // 发送游戏状态更新
            sendGameStateUpdate(gameId, userId, username);

        } catch (Exception e) {
            log.error("掷骰子失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    /**
     * 移动棋子
     * 客户端发送到: /app/game/{gameId}/move-piece
     * 广播到: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/move-piece")
    public void movePiece(@DestinationVariable Long gameId,
                          @Payload Map<String, Object> payload,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        Integer pieceIndex = (Integer) payload.get("pieceIndex");
        Integer diceResult = (Integer) payload.get("diceResult");

        log.info("移动棋子请求: gameId={}, userId={}, pieceIndex={}, diceResult={}",
                gameId, userId, pieceIndex, diceResult);

        try {
            // 验证玩家权限
            if (!gameService.validateGamePlayer(gameId, userId)) {
                sendError(gameId, "您不是这场游戏的参与者");
                return;
            }

            // 验证轮次
            if (!gameService.isPlayerTurn(gameId, userId)) {
                sendError(gameId, "还没轮到您");
                return;
            }

            // 移动棋子
            GameResponse gameResponse = gameService.movePiece(gameId, userId, pieceIndex, diceResult);

            // 获取移动前后位置
            Game game = gameService.getGameById(gameId);
            int playerNumber = game.getPlayer1Id().equals(userId) ? 1 : 2;
            List<Integer> playerPieces = playerNumber == 1 ?
                    gameResponse.getPlayer1Pieces() : gameResponse.getPlayer2Pieces();

            // 广播棋子移动
            GameMessage moveMessage = GameMessage.pieceMoved(gameId, userId, username,
                    pieceIndex, 0, playerPieces.get(pieceIndex), playerPieces);
            broadcastToGame(gameId, moveMessage);

            // 检查是否触发任务 - 使用游戏自己的taskPositions而不是引擎默认值
            int newPosition = playerPieces.get(pieceIndex);
            List<Integer> gameTaskPositions = game.getTaskPositions();
            boolean isTaskPosition = gameTaskPositions != null && gameTaskPositions.contains(newPosition);

            if (isTaskPosition) {
                // 从游戏的taskAssignments获取该位置对应的任务ID
                Map<String, Object> taskAssignments = game.getTaskAssignments();
                Long taskId = null;
                if (taskAssignments != null) {
                    Object taskIdObj = taskAssignments.get(String.valueOf(newPosition));
                    if (taskIdObj instanceof Long) {
                        taskId = (Long) taskIdObj;
                    } else if (taskIdObj instanceof Integer) {
                        taskId = ((Integer) taskIdObj).longValue();
                    } else if (taskIdObj instanceof Number) {
                        taskId = ((Number) taskIdObj).longValue();
                    }
                }

                GameTaskResponse task = null;
                if (taskId != null) {
                    // 获取预分配的任务
                    try {
                        task = gameTaskService.getTaskById(taskId);
                    } catch (Exception e) {
                        log.warn("获取预分配任务失败，使用随机任务: taskId={}", taskId);
                        task = gameTaskService.getRandomTask(null);
                    }
                } else {
                    // 如果没有预分配任务，使用随机任务
                    task = gameTaskService.getRandomTask(null);
                }

                if (task != null) {
                    // 确定执行者（另一方玩家）
                    Long executorPlayerId = game.getPlayer1Id().equals(userId)
                            ? game.getPlayer2Id()
                            : game.getPlayer1Id();

                    // 创建任务记录（如果有未完成的任务，会返回null）
                    GameTaskRecord record = gameTaskRecordService.triggerTask(
                            gameId, task.getId(), userId, executorPlayerId, newPosition);

                    // 只有成功创建任务记录时才广播
                    if (record != null) {
                        GameMessage taskMessage = GameMessage.taskTriggered(
                                gameId, record.getId(), task.getId(),
                                task.getTitle(), task.getDescription(), task.getPoints(),
                                userId, executorPlayerId, username);
                        broadcastToGame(gameId, taskMessage);
                    }
                }
            }

            // 检查游戏是否结束
            if (chessEngine.isGameOver(gameResponse)) {
                Long winnerId = chessEngine.determineWinner(gameResponse);
                if (winnerId != null) {
                    gameService.endGame(gameId, winnerId);
                    User winner = userMapper.selectById(winnerId);
                    GameMessage endMessage = GameMessage.gameEnded(gameId, winnerId,
                            winner != null ? winner.getRealName() : "玩家");
                    broadcastToGame(gameId, endMessage);
                }
            } else {
                // 发送轮次切换消息
                Game updatedGame = gameService.getGameById(gameId);
                Long currentPlayerId = updatedGame.getCurrentTurn() == 1 ?
                        updatedGame.getPlayer1Id() : updatedGame.getPlayer2Id();
                GameMessage turnMessage = GameMessage.turnChanged(gameId,
                        updatedGame.getCurrentTurn(), currentPlayerId);
                broadcastToGame(gameId, turnMessage);
            }

            // 发送游戏状态更新
            sendGameStateUpdate(gameId, userId, username);

        } catch (Exception e) {
            log.error("移动棋子失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    /**
     * 完成任务
     * 客户端发送到: /app/game/{gameId}/complete-task
     * 广播到: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/complete-task")
    public void completeTask(@DestinationVariable Long gameId,
                             @Payload Map<String, Object> payload,
                             SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        Long recordId = ((Number) payload.get("recordId")).longValue();
        String completionNote = (String) payload.get("completionNote");

        log.info("完成任务请求: gameId={}, userId={}, recordId={}", gameId, userId, recordId);

        try {
            gameTaskRecordService.completeTask(recordId, userId, completionNote);

            // 广播任务完成
            GameMessage message = GameMessage.builder()
                    .type(GameMessage.MessageType.TASK_COMPLETED)
                    .gameId(gameId)
                    .senderId(userId)
                    .senderName(username)
                    .data(Map.of("recordId", recordId, "completionNote", completionNote != null ? completionNote : ""))
                    .build();
            broadcastToGame(gameId, message);

        } catch (Exception e) {
            log.error("完成任务失败: gameId={}, userId={}, recordId={}", gameId, userId, recordId, e);
            sendError(gameId, e.getMessage());
        }
    }

    /**
     * 放弃任务
     * 客户端发送到: /app/game/{gameId}/abandon-task
     * 广播到: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/abandon-task")
    public void abandonTask(@DestinationVariable Long gameId,
                            @Payload Map<String, Object> payload,
                            SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        Long recordId = ((Number) payload.get("recordId")).longValue();

        log.info("放弃任务请求: gameId={}, userId={}, recordId={}", gameId, userId, recordId);

        try {
            gameTaskRecordService.abandonTask(recordId, userId);

            // 广播任务放弃
            GameMessage message = GameMessage.builder()
                    .type(GameMessage.MessageType.TASK_ABANDONED)
                    .gameId(gameId)
                    .senderId(userId)
                    .senderName(username)
                    .data(Map.of("recordId", recordId))
                    .build();
            broadcastToGame(gameId, message);

        } catch (Exception e) {
            log.error("放弃任务失败: gameId={}, userId={}, recordId={}", gameId, userId, recordId, e);
            sendError(gameId, e.getMessage());
        }
    }

    /**
     * 同步游戏状态
     * 客户端发送到: /app/game/{gameId}/sync
     * 响应到: /topic/game/{gameId}
     */
    @MessageMapping("/game/{gameId}/sync")
    public void syncGameState(@DestinationVariable Long gameId,
                              SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);
        String username = getUsernameFromSession(headerAccessor);

        log.info("同步游戏状态请求: gameId={}, userId={}", gameId, userId);

        try {
            sendGameStateUpdate(gameId, userId, username);
        } catch (Exception e) {
            log.error("同步游戏状态失败: gameId={}, userId={}", gameId, userId, e);
            sendError(gameId, e.getMessage());
        }
    }

    /**
     * 心跳
     * 客户端发送到: /app/game/{gameId}/heartbeat
     */
    @MessageMapping("/game/{gameId}/heartbeat")
    public void heartbeat(@DestinationVariable Long gameId,
                          SimpMessageHeaderAccessor headerAccessor) {
        Long userId = getUserIdFromSession(headerAccessor);

        try {
            gameService.updateLastMoveTime(gameId);
        } catch (Exception e) {
            log.debug("心跳更新失败: gameId={}, userId={}", gameId, userId);
        }
    }

    /**
     * 广播消息到游戏房间
     */
    private void broadcastToGame(Long gameId, GameMessage message) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
    }

    /**
     * 发送游戏状态更新
     */
    private void sendGameStateUpdate(Long gameId, Long userId, String username) {
        Game game = gameService.getGameById(gameId);
        GameMessage stateMessage = GameMessage.gameStateUpdate(
                gameId, userId, username,
                game.getCurrentTurn(),
                game.getPlayer1Pieces(),
                game.getPlayer2Pieces(),
                game.getLastDiceResult()
        );
        broadcastToGame(gameId, stateMessage);
    }

    /**
     * 发送错误消息
     */
    private void sendError(Long gameId, String errorMessage) {
        GameMessage message = GameMessage.error(gameId, errorMessage);
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
