package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * 上海麻将房间。座位 0..seatCount-1：seatN_user_id 是真人，bot_mask 第 N 位为 1 表示电脑，都为空就是空位。
 * stateData 是完整牌局（含牌墙和暗牌），绝不能原样返回给前端。
 * <p>
 * 会被清空的列（离座、取消邀请、清掉截止时间）用 FieldStrategy.ALWAYS，否则 updateById 会跳过 null 不写。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("mahjong_games")
public class MahjongGame {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("game_code")
    private String gameCode;

    @TableField("owner_id")
    private Long ownerId;

    /** {@link com.approval.system.common.enums.MahjongModeEnum} */
    @TableField("mode")
    private Integer mode;

    @TableField("seat_count")
    private Integer seatCount;

    @TableField("seat0_user_id")
    private Long seat0UserId;

    @TableField(value = "seat1_user_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long seat1UserId;

    @TableField(value = "seat2_user_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long seat2UserId;

    @TableField(value = "seat3_user_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long seat3UserId;

    @TableField("bot_mask")
    private Integer botMask;

    @TableField(value = "invited_user_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long invitedUserId;

    @TableField("cap_points")
    private Integer capPoints;

    /** {@link com.approval.system.common.enums.GameStatusEnum}：FINISHED 表示本局结束、可以再来一局 */
    @TableField("game_status")
    private Integer gameStatus;

    @TableField("round_no")
    private Integer roundNo;

    @TableField("dealer_seat")
    private Integer dealerSeat;

    @TableField("huang_count")
    private Integer huangCount;

    @TableField("total_scores")
    private String totalScores;

    @TableField("state_data")
    private String stateData;

    @TableField("state_version")
    private Integer stateVersion;

    @TableField(value = "action_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime actionDeadline;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
