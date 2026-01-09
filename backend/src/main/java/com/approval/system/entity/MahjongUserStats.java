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
 * 麻将用户统计实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("mahjong_user_stats")
public class MahjongUserStats {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 总游戏场次 */
    @TableField("total_games")
    private Integer totalGames;

    /** 总局数 */
    @TableField("total_rounds")
    private Integer totalRounds;

    /** 胡牌次数 */
    @TableField("win_count")
    private Integer winCount;

    /** 自摸次数 */
    @TableField("self_draw_count")
    private Integer selfDrawCount;

    /** 累计积分 */
    @TableField("total_score")
    private Integer totalScore;

    /** 最高番数 */
    @TableField("max_fan")
    private Integer maxFan;

    /** 四百搭次数 */
    @TableField("four_wild_count")
    private Integer fourWildCount;

    /** 无百搭次数 */
    @TableField("no_wild_count")
    private Integer noWildCount;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 创建默认统计记录
     */
    public static MahjongUserStats createDefault(Long userId) {
        return MahjongUserStats.builder()
                .userId(userId)
                .totalGames(0)
                .totalRounds(0)
                .winCount(0)
                .selfDrawCount(0)
                .totalScore(0)
                .maxFan(0)
                .fourWildCount(0)
                .noWildCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
