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
import java.util.Map;

/**
 * 麻将单局记录实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName(value = "mahjong_rounds", autoResultMap = true)
public class MahjongRound {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属游戏ID */
    @TableField("game_id")
    private Long gameId;

    /** 第几局 */
    @TableField("round_number")
    private Integer roundNumber;

    // ========== 局状态 ==========

    /** 局状态: 1=进行中, 2=流局, 3=胡牌结束 */
    @TableField("round_status")
    private Integer roundStatus;

    /** 本局庄家座位 1-4 */
    @TableField("dealer_seat")
    private Integer dealerSeat;

    /** 当前操作玩家座位 1-4 */
    @TableField("current_turn")
    private Integer currentTurn;

    // ========== 牌局数据 (JSON) ==========

    /** 牌墙剩余牌 */
    @TableField(value = "wall_tiles", typeHandler = JacksonTypeHandler.class)
    private List<String> wallTiles;

    /** 玩家1手牌 */
    @TableField(value = "player1_hand", typeHandler = JacksonTypeHandler.class)
    private List<String> player1Hand;

    /** 玩家2手牌 */
    @TableField(value = "player2_hand", typeHandler = JacksonTypeHandler.class)
    private List<String> player2Hand;

    /** 玩家3手牌 */
    @TableField(value = "player3_hand", typeHandler = JacksonTypeHandler.class)
    private List<String> player3Hand;

    /** 玩家4手牌 */
    @TableField(value = "player4_hand", typeHandler = JacksonTypeHandler.class)
    private List<String> player4Hand;

    /** 玩家1明牌(碰/杠) */
    @TableField(value = "player1_melds", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> player1Melds;

    /** 玩家2明牌 */
    @TableField(value = "player2_melds", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> player2Melds;

    /** 玩家3明牌 */
    @TableField(value = "player3_melds", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> player3Melds;

    /** 玩家4明牌 */
    @TableField(value = "player4_melds", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> player4Melds;

    /** 玩家1弃牌区 */
    @TableField(value = "player1_discards", typeHandler = JacksonTypeHandler.class)
    private List<String> player1Discards;

    /** 玩家2弃牌区 */
    @TableField(value = "player2_discards", typeHandler = JacksonTypeHandler.class)
    private List<String> player2Discards;

    /** 玩家3弃牌区 */
    @TableField(value = "player3_discards", typeHandler = JacksonTypeHandler.class)
    private List<String> player3Discards;

    /** 玩家4弃牌区 */
    @TableField(value = "player4_discards", typeHandler = JacksonTypeHandler.class)
    private List<String> player4Discards;

    /** 玩家1花牌 */
    @TableField(value = "player1_flowers", typeHandler = JacksonTypeHandler.class)
    private List<String> player1Flowers;

    /** 玩家2花牌 */
    @TableField(value = "player2_flowers", typeHandler = JacksonTypeHandler.class)
    private List<String> player2Flowers;

    /** 玩家3花牌 */
    @TableField(value = "player3_flowers", typeHandler = JacksonTypeHandler.class)
    private List<String> player3Flowers;

    /** 玩家4花牌 */
    @TableField(value = "player4_flowers", typeHandler = JacksonTypeHandler.class)
    private List<String> player4Flowers;

    // ========== 最后操作信息 ==========

    /** 最后打出/摸到的牌 */
    @TableField("last_tile")
    private String lastTile;

    /** 最后操作类型 */
    @TableField("last_action")
    private String lastAction;

    /** 最后操作者座位 */
    @TableField("last_action_seat")
    private Integer lastActionSeat;

    /** 待处理的响应操作 */
    @TableField(value = "pending_actions", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> pendingActions;

    // ========== 结算信息 ==========

    /** 胡牌者座位 */
    @TableField("winner_seat")
    private Integer winnerSeat;

    /** 胡牌类型 */
    @TableField("hu_type")
    private String huType;

    /** 总番数 */
    @TableField("fan_count")
    private Integer fanCount;

    /** 分数变化 */
    @TableField(value = "score_changes", typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> scoreChanges;

    // ========== 时间 ==========

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    /**
     * 获取指定座位的手牌
     */
    public List<String> getHandBySeat(int seat) {
        switch (seat) {
            case 1: return player1Hand;
            case 2: return player2Hand;
            case 3: return player3Hand;
            case 4: return player4Hand;
            default: return null;
        }
    }

    /**
     * 设置指定座位的手牌
     */
    public void setHandBySeat(int seat, List<String> hand) {
        switch (seat) {
            case 1: player1Hand = hand; break;
            case 2: player2Hand = hand; break;
            case 3: player3Hand = hand; break;
            case 4: player4Hand = hand; break;
        }
    }

    /**
     * 获取指定座位的明牌
     */
    public List<Map<String, Object>> getMeldsBySeat(int seat) {
        switch (seat) {
            case 1: return player1Melds;
            case 2: return player2Melds;
            case 3: return player3Melds;
            case 4: return player4Melds;
            default: return null;
        }
    }

    /**
     * 设置指定座位的明牌
     */
    public void setMeldsBySeat(int seat, List<Map<String, Object>> melds) {
        switch (seat) {
            case 1: player1Melds = melds; break;
            case 2: player2Melds = melds; break;
            case 3: player3Melds = melds; break;
            case 4: player4Melds = melds; break;
        }
    }

    /**
     * 获取指定座位的弃牌
     */
    public List<String> getDiscardsBySeat(int seat) {
        switch (seat) {
            case 1: return player1Discards;
            case 2: return player2Discards;
            case 3: return player3Discards;
            case 4: return player4Discards;
            default: return null;
        }
    }

    /**
     * 设置指定座位的弃牌
     */
    public void setDiscardsBySeat(int seat, List<String> discards) {
        switch (seat) {
            case 1: player1Discards = discards; break;
            case 2: player2Discards = discards; break;
            case 3: player3Discards = discards; break;
            case 4: player4Discards = discards; break;
        }
    }

    /**
     * 获取指定座位的花牌
     */
    public List<String> getFlowersBySeat(int seat) {
        switch (seat) {
            case 1: return player1Flowers;
            case 2: return player2Flowers;
            case 3: return player3Flowers;
            case 4: return player4Flowers;
            default: return null;
        }
    }

    /**
     * 设置指定座位的花牌
     */
    public void setFlowersBySeat(int seat, List<String> flowers) {
        switch (seat) {
            case 1: player1Flowers = flowers; break;
            case 2: player2Flowers = flowers; break;
            case 3: player3Flowers = flowers; break;
            case 4: player4Flowers = flowers; break;
        }
    }
}
