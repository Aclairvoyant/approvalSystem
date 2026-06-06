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
@TableName("gobang_moves")
public class GobangMove {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_id")
    private Long gameId;

    @TableField("player_id")
    private Long playerId;

    @TableField("move_number")
    private Integer moveNumber;

    @TableField("move_type")
    private String moveType;

    @TableField("row_index")
    private Integer rowIndex;

    @TableField("col_index")
    private Integer colIndex;

    @TableField("color")
    private Integer color;

    @TableField("move_data")
    private String moveData;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
