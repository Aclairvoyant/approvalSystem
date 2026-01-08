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
@TableName("game_moves")
public class GameMove {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_id")
    private Long gameId;

    @TableField("player_id")
    private Long playerId;

    @TableField("move_type")
    private Integer moveType;

    @TableField("dice_result")
    private Integer diceResult;

    @TableField("piece_index")
    private Integer pieceIndex;

    @TableField("from_position")
    private Integer fromPosition;

    @TableField("to_position")
    private Integer toPosition;

    @TableField("captured_piece_index")
    private Integer capturedPieceIndex;

    @TableField("task_id")
    private Long taskId;

    @TableField("move_data")
    private String moveData;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
