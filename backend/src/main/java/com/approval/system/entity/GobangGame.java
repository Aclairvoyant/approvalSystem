package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("gobang_games")
public class GobangGame {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_code")
    private String gameCode;

    @TableField("black_player_id")
    private Long blackPlayerId;

    @TableField("invited_player_id")
    private Long invitedPlayerId;

    @TableField("white_player_id")
    private Long whitePlayerId;

    @TableField("current_turn")
    private Integer currentTurn;

    @TableField("game_status")
    private Integer gameStatus;

    @TableField("winner_id")
    private Long winnerId;

    @TableField("board_data")
    private String boardData;

    @TableField("last_move_id")
    private Long lastMoveId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
