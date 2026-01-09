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
 * 麻将操作记录实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("mahjong_actions")
public class MahjongAction {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属单局ID */
    @TableField("round_id")
    private Long roundId;

    /** 操作者座位 1-4 */
    @TableField("player_seat")
    private Integer playerSeat;

    /** 操作类型 */
    @TableField("action_type")
    private String actionType;

    /** 相关的牌 */
    @TableField("tile")
    private String tile;

    /** 额外数据 (JSON字符串) */
    @TableField("action_data")
    private String actionData;

    /** 操作时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
