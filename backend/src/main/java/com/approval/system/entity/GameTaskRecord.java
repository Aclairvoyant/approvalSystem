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
@TableName("game_task_records")
public class GameTaskRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_id")
    private Long gameId;

    @TableField("task_id")
    private Long taskId;

    @TableField("trigger_player_id")
    private Long triggerPlayerId;

    @TableField("executor_player_id")
    private Long executorPlayerId;

    @TableField("task_status")
    private Integer taskStatus;

    @TableField("completion_note")
    private String completionNote;

    @TableField("triggered_position")
    private Integer triggeredPosition;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
