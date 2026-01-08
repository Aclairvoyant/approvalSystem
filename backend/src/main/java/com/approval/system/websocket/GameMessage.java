package com.approval.system.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 游戏WebSocket消息格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 游戏ID
     */
    private Long gameId;

    /**
     * 发送者用户ID
     */
    private Long senderId;

    /**
     * 发送者用户名
     */
    private String senderName;

    /**
     * 消息数据（根据类型不同而变化）
     */
    private Map<String, Object> data;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        // 连接相关
        CONNECTED,           // 已连接
        DISCONNECTED,        // 已断开
        PLAYER_JOINED,       // 玩家加入
        PLAYER_LEFT,         // 玩家离开

        // 游戏状态
        GAME_STARTED,        // 游戏开始
        GAME_ENDED,          // 游戏结束
        GAME_STATE_UPDATE,   // 游戏状态更新

        // 游戏操作
        DICE_ROLLED,         // 骰子结果
        PIECE_MOVED,         // 棋子移动
        PIECE_CAPTURED,      // 吃子
        TURN_CHANGED,        // 轮次切换

        // 任务相关
        TASK_TRIGGERED,      // 触发任务
        TASK_COMPLETED,      // 完成任务
        TASK_ABANDONED,      // 放弃任务
        TASK_TIMEOUT,        // 任务超时

        // 系统消息
        ERROR,               // 错误消息
        HEARTBEAT,           // 心跳
        SYNC_REQUEST,        // 同步请求
        SYNC_RESPONSE        // 同步响应
    }

    /**
     * 创建游戏状态更新消息
     */
    public static GameMessage gameStateUpdate(Long gameId, Long senderId, String senderName,
                                              Integer currentTurn,
                                              List<Integer> player1Pieces,
                                              List<Integer> player2Pieces,
                                              Integer lastDiceResult) {
        return GameMessage.builder()
                .type(MessageType.GAME_STATE_UPDATE)
                .gameId(gameId)
                .senderId(senderId)
                .senderName(senderName)
                .data(Map.of(
                        "currentTurn", currentTurn,
                        "player1Pieces", player1Pieces,
                        "player2Pieces", player2Pieces,
                        "lastDiceResult", lastDiceResult != null ? lastDiceResult : 0
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建骰子结果消息
     */
    public static GameMessage diceRolled(Long gameId, Long playerId, String playerName, Integer result) {
        return GameMessage.builder()
                .type(MessageType.DICE_ROLLED)
                .gameId(gameId)
                .senderId(playerId)
                .senderName(playerName)
                .data(Map.of("diceResult", result))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建棋子移动消息
     */
    public static GameMessage pieceMoved(Long gameId, Long playerId, String playerName,
                                         Integer pieceIndex, Integer fromPosition, Integer toPosition,
                                         List<Integer> playerPieces) {
        return GameMessage.builder()
                .type(MessageType.PIECE_MOVED)
                .gameId(gameId)
                .senderId(playerId)
                .senderName(playerName)
                .data(Map.of(
                        "pieceIndex", pieceIndex,
                        "fromPosition", fromPosition,
                        "toPosition", toPosition,
                        "playerPieces", playerPieces
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建吃子消息
     */
    public static GameMessage pieceCaptured(Long gameId, Long capturedPlayerId,
                                            Integer capturedPieceIndex) {
        return GameMessage.builder()
                .type(MessageType.PIECE_CAPTURED)
                .gameId(gameId)
                .data(Map.of(
                        "capturedPlayerId", capturedPlayerId,
                        "capturedPieceIndex", capturedPieceIndex
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建任务触发消息
     */
    public static GameMessage taskTriggered(Long gameId, Long recordId, Long taskId,
                                            String taskTitle, String taskDescription,
                                            Integer points, Long triggerPlayerId,
                                            Long executorPlayerId, String triggerPlayerName) {
        return GameMessage.builder()
                .type(MessageType.TASK_TRIGGERED)
                .gameId(gameId)
                .senderId(triggerPlayerId)
                .senderName(triggerPlayerName)
                .data(Map.of(
                        "recordId", recordId,
                        "taskId", taskId,
                        "taskTitle", taskTitle,
                        "taskDescription", taskDescription != null ? taskDescription : "",
                        "points", points,
                        "triggerPlayerId", triggerPlayerId,
                        "executorPlayerId", executorPlayerId
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建游戏结束消息
     */
    public static GameMessage gameEnded(Long gameId, Long winnerId, String winnerName) {
        return GameMessage.builder()
                .type(MessageType.GAME_ENDED)
                .gameId(gameId)
                .data(Map.of(
                        "winnerId", winnerId,
                        "winnerName", winnerName
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建错误消息
     */
    public static GameMessage error(Long gameId, String errorMessage) {
        return GameMessage.builder()
                .type(MessageType.ERROR)
                .gameId(gameId)
                .data(Map.of("error", errorMessage))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建玩家加入消息
     */
    public static GameMessage playerJoined(Long gameId, Long playerId, String playerName) {
        return GameMessage.builder()
                .type(MessageType.PLAYER_JOINED)
                .gameId(gameId)
                .senderId(playerId)
                .senderName(playerName)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建轮次切换消息
     */
    public static GameMessage turnChanged(Long gameId, Integer currentTurn, Long currentPlayerId) {
        return GameMessage.builder()
                .type(MessageType.TURN_CHANGED)
                .gameId(gameId)
                .data(Map.of(
                        "currentTurn", currentTurn,
                        "currentPlayerId", currentPlayerId
                ))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
