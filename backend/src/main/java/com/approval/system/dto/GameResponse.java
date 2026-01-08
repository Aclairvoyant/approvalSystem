package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameResponse {
    private Long id;
    private String gameCode;
    private Long player1Id;
    private String player1Name;
    private String player1Avatar;
    private Long player2Id;
    private String player2Name;
    private String player2Avatar;
    private Integer currentTurn;
    private Integer gameStatus;
    private Long winnerId;
    private List<Integer> player1Pieces;
    private List<Integer> player2Pieces;
    private Integer lastDiceResult;
    private List<Integer> taskPositions;  // 任务触发位置
    private List<TaskPositionInfo> taskInfos;  // 任务位置详细信息（位置和标题）
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /**
     * 任务位置信息，包含位置和任务标题
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TaskPositionInfo {
        private Integer position;
        private Long taskId;
        private String title;
    }
}
