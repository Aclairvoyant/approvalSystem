package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 麻将游戏响应（包含完整游戏状态）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongGameResponse {

    // ========== 基础信息 ==========
    private Long id;
    private String gameCode;
    private Long currentUserId;  // 当前请求的用户ID（用于验证）

    // ========== 游戏配置 ==========
    private Integer ruleType;
    private String ruleTypeName;  // "敲麻" 或 "百搭"
    private Integer flowerMode;
    private Integer playerCount;
    private Integer totalRounds;
    private Integer baseScore;
    private Integer maxScore;
    private Integer flyCount;

    // ========== 百搭配置 ==========
    private String wildTile;      // 百搭牌
    private String guideTile;     // 前端引导牌
    private Integer dice1;
    private Integer dice2;

    // ========== 玩家信息 ==========
    private Long player1Id;
    private String player1Name;
    private String player1Avatar;
    private Long player2Id;
    private String player2Name;
    private String player2Avatar;
    private Long player3Id;
    private String player3Name;
    private String player3Avatar;
    private Long player4Id;
    private String player4Name;
    private String player4Avatar;

    // ========== 游戏状态 ==========
    private Integer gameStatus;
    private String gameStatusName;
    private Integer currentRound;
    private Integer dealerSeat;

    // ========== 积分 ==========
    private Integer player1Score;
    private Integer player2Score;
    private Integer player3Score;
    private Integer player4Score;

    // ========== 当前局状态（如果游戏中） ==========
    private MahjongRoundResponse currentRoundData;

    // ========== 时间戳 ==========
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /**
     * 单局响应
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MahjongRoundResponse {
        private Long id;
        private Integer roundNumber;
        private Integer roundStatus;
        private Integer dealerSeat;
        private Integer currentTurn;

        // 牌墙剩余数量（不暴露具体牌）
        private Integer wallRemaining;

        // 当前玩家手牌（仅自己可见）
        private List<String> myHand;
        private Integer mySeat;

        // 所有玩家的明牌（公开）
        private List<MeldInfo> player1Melds;
        private List<MeldInfo> player2Melds;
        private List<MeldInfo> player3Melds;
        private List<MeldInfo> player4Melds;

        // 所有玩家的弃牌（公开）
        private List<String> player1Discards;
        private List<String> player2Discards;
        private List<String> player3Discards;
        private List<String> player4Discards;

        // 所有玩家的花牌（公开）
        private List<String> player1Flowers;
        private List<String> player2Flowers;
        private List<String> player3Flowers;
        private List<String> player4Flowers;

        // 其他玩家手牌数量
        private Integer player1HandCount;
        private Integer player2HandCount;
        private Integer player3HandCount;
        private Integer player4HandCount;

        // 最后操作
        private String lastTile;
        private String lastAction;
        private Integer lastActionSeat;

        // 可执行的操作
        private List<String> availableActions;

        // 吃牌选项（多种吃法）
        private List<List<String>> chiOptions;

        // 结算信息（局结束时）
        private Integer winnerSeat;
        private String huType;
        private Integer fanCount;
        private Map<String, Integer> scoreChanges;
    }

    /**
     * 明牌信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MeldInfo {
        private String type;        // PONG/MING_KONG/AN_KONG/BU_KONG
        private List<String> tiles; // 组成的牌
        private Boolean concealed;  // 是否暗杠
    }
}
