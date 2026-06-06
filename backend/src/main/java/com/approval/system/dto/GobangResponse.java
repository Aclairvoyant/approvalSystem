package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GobangResponse {

    private Long id;
    private String gameCode;
    private Long blackPlayerId;
    private String blackPlayerName;
    private String blackPlayerAvatar;
    private Long invitedPlayerId;
    private String invitedPlayerName;
    private String invitedPlayerAvatar;
    private Long whitePlayerId;
    private String whitePlayerName;
    private String whitePlayerAvatar;
    private Integer currentTurn;
    private Integer gameStatus;
    private Long winnerId;
    private String boardData;
    private Long lastMoveId;
    private Long pendingUndoRequesterId;
    private Long pendingUndoResponderId;
    private Integer pendingUndoTargetMoveNumber;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime updatedAt;
}
