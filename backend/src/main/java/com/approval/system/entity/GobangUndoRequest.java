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
@TableName("gobang_undo_requests")
public class GobangUndoRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_id")
    private Long gameId;

    @TableField("requester_id")
    private Long requesterId;

    @TableField("responder_id")
    private Long responderId;

    @TableField("target_move_number")
    private Integer targetMoveNumber;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("responded_at")
    private LocalDateTime respondedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
