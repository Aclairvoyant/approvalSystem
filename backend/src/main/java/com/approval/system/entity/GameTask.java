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
@TableName("game_tasks")
public class GameTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_type")
    private Integer taskType;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("category")
    private String category;

    @TableField("difficulty")
    private Integer difficulty;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("requirement")
    private String requirement;

    @TableField("time_limit")
    private Integer timeLimit;

    @TableField("points")
    private Integer points;

    @TableField("is_active")
    private Integer isActive;

    @TableField("usage_count")
    private Integer usageCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
