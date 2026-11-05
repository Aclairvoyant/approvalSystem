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

/**
 * 麻将动作日志（含摸牌这类隐藏信息，只用于服务端审计）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("mahjong_moves")
public class MahjongMove {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_id")
    private Long gameId;

    @TableField("round_no")
    private Integer roundNo;

    @TableField("move_number")
    private Integer moveNumber;

    @TableField("seat_index")
    private Integer seatIndex;

    @TableField("player_id")
    private Long playerId;

    @TableField("action_type")
    private String actionType;

    @TableField("tile")
    private Integer tile;

    @TableField("move_data")
    private String moveData;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
