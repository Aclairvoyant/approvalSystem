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
 * 麻将游戏实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName(value = "mahjong_games", autoResultMap = true)
public class MahjongGame {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 房间号 */
    @TableField("game_code")
    private String gameCode;

    // ========== 游戏配置 ==========

    /** 规则类型: 1=敲麻, 2=百搭 */
    @TableField("rule_type")
    private Integer ruleType;

    /** 花牌模式: 8=8花, 20=20花, 36=36花 */
    @TableField("flower_mode")
    private Integer flowerMode;

    /** 玩家人数: 2/3/4 */
    @TableField("player_count")
    private Integer playerCount;

    /** 总局数 */
    @TableField("total_rounds")
    private Integer totalRounds;

    /** 底分 */
    @TableField("base_score")
    private Integer baseScore;

    /** 封顶分数，NULL表示无封顶 */
    @TableField("max_score")
    private Integer maxScore;

    /** 飞苍蝇数量 */
    @TableField("fly_count")
    private Integer flyCount;

    // ========== 百搭配置 ==========

    /** 百搭牌 (如 "4WAN") */
    @TableField("wild_tile")
    private String wildTile;

    /** 前端引导牌 */
    @TableField("guide_tile")
    private String guideTile;

    /** 第一个骰子点数 */
    @TableField("dice1")
    private Integer dice1;

    /** 第二个骰子点数 */
    @TableField("dice2")
    private Integer dice2;

    /** 开始取牌的玩家座位 */
    @TableField("wall_start_seat")
    private Integer wallStartSeat;

    /** 牌墙起始位置 */
    @TableField("wall_start_pos")
    private Integer wallStartPos;

    // ========== 玩家信息 ==========

    /** 玩家1（房主）ID */
    @TableField("player1_id")
    private Long player1Id;

    /** 玩家2 ID */
    @TableField("player2_id")
    private Long player2Id;

    /** 玩家3 ID */
    @TableField("player3_id")
    private Long player3Id;

    /** 玩家4 ID */
    @TableField("player4_id")
    private Long player4Id;

    // ========== 游戏状态 ==========

    /** 游戏状态: 1=等待, 2=进行中, 3=已结束, 4=已取消 */
    @TableField("game_status")
    private Integer gameStatus;

    /** 当前局数 */
    @TableField("current_round")
    private Integer currentRound;

    /** 当前庄家座位 1-4 */
    @TableField("dealer_seat")
    private Integer dealerSeat;

    // ========== 积分 ==========

    @TableField("player1_score")
    private Integer player1Score;

    @TableField("player2_score")
    private Integer player2Score;

    @TableField("player3_score")
    private Integer player3Score;

    @TableField("player4_score")
    private Integer player4Score;

    // ========== 时间戳 ==========

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 获取指定座位的玩家ID
     */
    public Long getPlayerIdBySeat(int seat) {
        switch (seat) {
            case 1: return player1Id;
            case 2: return player2Id;
            case 3: return player3Id;
            case 4: return player4Id;
            default: return null;
        }
    }

    /**
     * 设置指定座位的玩家ID
     */
    public void setPlayerIdBySeat(int seat, Long playerId) {
        switch (seat) {
            case 1: player1Id = playerId; break;
            case 2: player2Id = playerId; break;
            case 3: player3Id = playerId; break;
            case 4: player4Id = playerId; break;
        }
    }

    /**
     * 获取指定座位的分数
     */
    public Integer getScoreBySeat(int seat) {
        switch (seat) {
            case 1: return player1Score;
            case 2: return player2Score;
            case 3: return player3Score;
            case 4: return player4Score;
            default: return 0;
        }
    }

    /**
     * 设置指定座位的分数
     */
    public void setScoreBySeat(int seat, Integer score) {
        switch (seat) {
            case 1: player1Score = score; break;
            case 2: player2Score = score; break;
            case 3: player3Score = score; break;
            case 4: player4Score = score; break;
        }
    }

    /**
     * 获取玩家的座位号
     */
    public Integer getSeatByPlayerId(Long playerId) {
        if (playerId == null) return null;
        if (playerId.equals(player1Id)) return 1;
        if (playerId.equals(player2Id)) return 2;
        if (playerId.equals(player3Id)) return 3;
        if (playerId.equals(player4Id)) return 4;
        return null;
    }

    /**
     * 获取当前加入的玩家数量
     */
    public int getJoinedPlayerCount() {
        int count = 0;
        if (player1Id != null) count++;
        if (player2Id != null) count++;
        if (player3Id != null) count++;
        if (player4Id != null) count++;
        return count;
    }

    /**
     * 检查玩家是否在游戏中
     */
    public boolean hasPlayer(Long playerId) {
        return getSeatByPlayerId(playerId) != null;
    }

    /**
     * 是否是百搭模式
     */
    public boolean isBaidaMode() {
        return ruleType != null && ruleType == 2;
    }
}
