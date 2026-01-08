package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("user_game_stats")
public class UserGameStats {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("total_games")
    private Integer totalGames;

    @TableField("win_count")
    private Integer winCount;

    @TableField("lose_count")
    private Integer loseCount;

    @TableField("draw_count")
    private Integer drawCount;

    @TableField("total_tasks_completed")
    private Integer totalTasksCompleted;

    @TableField("total_points")
    private Integer totalPoints;

    @TableField("win_rate")
    private BigDecimal winRate;

    @TableField("favorite_task_category")
    private String favoriteTaskCategory;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
