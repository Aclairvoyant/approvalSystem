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
public class GobangMoveResponse {

    private Long id;
    private Long gameId;
    private Long playerId;
    private String playerName;
    private String playerAvatar;
    private Integer moveNumber;
    private String moveType;
    private Integer rowIndex;
    private Integer colIndex;
    private Integer color;
    private String moveData;
    private LocalDateTime createdAt;
}
