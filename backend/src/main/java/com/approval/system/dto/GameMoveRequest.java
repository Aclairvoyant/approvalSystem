package com.approval.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameMoveRequest {
    @NotNull(message = "游戏ID不能为空")
    private Long gameId;

    @NotNull(message = "棋子索引不能为空")
    private Integer pieceIndex;

    private Integer diceResult;
}
