package com.approval.system.websocket;

import com.approval.system.dto.GobangResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GobangMessage {

    private MessageType type;
    private Long gameId;
    private Long senderId;
    private String senderName;
    private Map<String, Object> data;
    private LocalDateTime timestamp;

    public enum MessageType {
        GOBANG_STATE_UPDATE,
        GOBANG_STONE_PLACED,
        GOBANG_UNDO_REQUESTED,
        GOBANG_UNDO_RESOLVED,
        GOBANG_PLAYER_SURRENDERED,
        GOBANG_GAME_ENDED,
        GOBANG_ERROR
    }

    public static GobangMessage stateUpdate(Long gameId, Long senderId, String senderName, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("game", response);
        return build(MessageType.GOBANG_STATE_UPDATE, gameId, senderId, senderName, data);
    }

    public static GobangMessage stonePlaced(Long gameId, Long senderId, String senderName,
                                            Integer row, Integer col, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("row", row);
        data.put("col", col);
        data.put("game", response);
        return build(MessageType.GOBANG_STONE_PLACED, gameId, senderId, senderName, data);
    }

    public static GobangMessage undoRequested(Long gameId, Long senderId, String senderName, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requesterId", response.getPendingUndoRequesterId() != null
                ? response.getPendingUndoRequesterId()
                : senderId);
        data.put("responderId", response.getPendingUndoResponderId());
        data.put("targetMoveNumber", response.getPendingUndoTargetMoveNumber());
        data.put("game", response);
        return build(MessageType.GOBANG_UNDO_REQUESTED, gameId, senderId, senderName, data);
    }

    public static GobangMessage undoResolved(Long gameId, Long senderId, String senderName,
                                             Boolean accepted, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", Boolean.TRUE.equals(accepted));
        data.put("game", response);
        return build(MessageType.GOBANG_UNDO_RESOLVED, gameId, senderId, senderName, data);
    }

    public static GobangMessage surrendered(Long gameId, Long senderId, String senderName, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("game", response);
        return build(MessageType.GOBANG_PLAYER_SURRENDERED, gameId, senderId, senderName, data);
    }

    public static GobangMessage gameEnded(Long gameId, GobangResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("game", response);
        data.put("winnerId", response.getWinnerId());
        return build(MessageType.GOBANG_GAME_ENDED, gameId, null, null, data);
    }

    public static GobangMessage error(Long gameId, String errorMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error", errorMessage);
        return build(MessageType.GOBANG_ERROR, gameId, null, null, data);
    }

    private static GobangMessage build(MessageType type, Long gameId, Long senderId,
                                       String senderName, Map<String, Object> data) {
        return GobangMessage.builder()
                .type(type)
                .gameId(gameId)
                .senderId(senderId)
                .senderName(senderName)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
