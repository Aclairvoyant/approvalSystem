package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
@TableName(value = "games", autoResultMap = true)
public class Game {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_code")
    private String gameCode;

    @TableField("player1_id")
    private Long player1Id;

    @TableField("player2_id")
    private Long player2Id;

    @TableField("current_turn")
    private Integer currentTurn;

    @TableField("game_status")
    private Integer gameStatus;

    @TableField("winner_id")
    private Long winnerId;

    @TableField(value = "board_data")
    private String boardData;

    @TableField(value = "player1_pieces", typeHandler = JacksonTypeHandler.class)
    private List<Integer> player1Pieces;

    @TableField(value = "player2_pieces", typeHandler = JacksonTypeHandler.class)
    private List<Integer> player2Pieces;

    @TableField("last_dice_result")
    private Integer lastDiceResult;

    @TableField("last_move_time")
    private LocalDateTime lastMoveTime;

    /**
     * 自定义任务位置数组，如 [5,10,15,20,25]
     */
    @TableField(value = "task_positions", typeHandler = JacksonTypeHandler.class)
    private List<Integer> taskPositions;

    /**
     * 每个任务位置分配的任务ID映射，如 {"5": 1, "10": 3, "15": 5}
     * 注意：JSON反序列化后值可能是Integer或Long，使用时需要处理类型转换
     */
    @TableField(value = "task_assignments", typeHandler = JacksonTypeHandler.class)
    private java.util.Map<String, Object> taskAssignments;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
