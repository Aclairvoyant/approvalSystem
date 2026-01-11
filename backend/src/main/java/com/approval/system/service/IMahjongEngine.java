package com.approval.system.service;

import com.approval.system.entity.MahjongRound;
import com.approval.system.entity.MahjongTile;
import com.approval.system.common.enums.MahjongActionType;
import com.approval.system.common.enums.MahjongHuType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 麻将游戏引擎接口
 * 定义麻将游戏的核心规则和操作
 */
public interface IMahjongEngine {

    // ==================== 初始化 ====================

    /**
     * 初始化一局游戏
     * @param round 当前局数据
     * @param playerCount 玩家人数
     * @param flowerMode 花牌模式(8/20/36)
     */
    void initRound(MahjongRound round, int playerCount, int flowerMode);

    /**
     * 洗牌并发牌
     * @param dealerSeat 庄家座位(1-4)
     * @return 牌墙剩余牌
     */
    List<MahjongTile> shuffleAndDeal(int dealerSeat);

    /**
     * 获取玩家手牌
     * @param seat 座位号(1-4)
     * @return 手牌列表
     */
    List<MahjongTile> getPlayerHand(int seat);

    /**
     * 获取玩家花牌
     * @param seat 座位号(1-4)
     * @return 花牌列表
     */
    List<MahjongTile> getPlayerFlowers(int seat);

    /**
     * 获取玩家明牌(碰/杠)
     * @param seat 座位号(1-4)
     * @return 明牌列表
     */
    List<Meld> getPlayerMelds(int seat);

    /**
     * 获取玩家弃牌
     * @param seat 座位号(1-4)
     * @return 弃牌列表
     */
    List<MahjongTile> getPlayerDiscards(int seat);

    /**
     * 获取牌墙剩余数量
     * @return 剩余牌数
     */
    int getWallRemaining();

    /**
     * 获取牌墙剩余牌
     * @return 牌墙列表
     */
    List<MahjongTile> getWall();

    /**
     * 设置牌墙(用于状态恢复)
     * @param wall 牌墙列表
     */
    void setWall(List<MahjongTile> wall);

    // ==================== 操作验证 ====================

    /**
     * 检查是否可以摸牌
     * @param seat 座位号
     * @return 是否可以摸牌
     */
    boolean canDraw(int seat);

    /**
     * 检查是否可以出牌
     * @param seat 座位号
     * @param tile 要出的牌
     * @return 是否可以出牌
     */
    boolean canDiscard(int seat, MahjongTile tile);

    /**
     * 检查是否可以碰
     * @param seat 座位号
     * @param discardedTile 被打出的牌
     * @return 是否可以碰
     */
    boolean canPong(int seat, MahjongTile discardedTile);

    /**
     * 检查是否可以吃(只能吃上家的牌)
     * @param seat 座位号
     * @param discardedTile 被打出的牌
     * @param fromSeat 出牌者座位
     * @return 可以吃的牌组合列表，每个组合是两张手牌的编码
     */
    List<List<String>> getChiOptions(int seat, MahjongTile discardedTile, int fromSeat);

    /**
     * 检查是否可以明杠(别人打出的牌,手中有三张)
     * @param seat 座位号
     * @param discardedTile 被打出的牌
     * @return 是否可以明杠
     */
    boolean canMingKong(int seat, MahjongTile discardedTile);

    /**
     * 检查是否可以暗杠(手中四张相同)
     * @param seat 座位号
     * @return 可以暗杠的牌列表
     */
    List<MahjongTile> getAnKongOptions(int seat);

    /**
     * 检查是否可以补杠(已碰后摸到第四张)
     * @param seat 座位号
     * @return 可以补杠的牌列表
     */
    List<MahjongTile> getBuKongOptions(int seat);

    /**
     * 检查是否可以胡牌(自摸)
     * @param seat 座位号
     * @return 胡牌结果,null表示不能胡
     */
    HuResult canHu(int seat);

    /**
     * 获取玩家可执行的操作
     * @param seat 座位号
     * @param discardedTile 最后打出的牌(可为null)
     * @param isMyTurn 是否是该玩家的回合
     * @return 可执行的操作类型列表
     */
    List<MahjongActionType> getAvailableActions(int seat, MahjongTile discardedTile, boolean isMyTurn);

    // ==================== 执行操作 ====================

    /**
     * 摸牌
     * @param seat 座位号
     * @return 摸到的牌
     */
    MahjongTile draw(int seat);

    /**
     * 出牌
     * @param seat 座位号
     * @param tile 要出的牌
     */
    void discard(int seat, MahjongTile tile);

    /**
     * 碰牌
     * @param seat 座位号
     * @param discardedTile 碰的牌
     * @param fromSeat 来源座位
     */
    void pong(int seat, MahjongTile discardedTile, int fromSeat);

    /**
     * 吃牌
     * @param seat 座位号
     * @param discardedTile 吃的牌
     * @param fromSeat 来源座位
     * @param chiTiles 用于吃的两张手牌
     */
    void chi(int seat, MahjongTile discardedTile, int fromSeat, List<MahjongTile> chiTiles);

    /**
     * 明杠(别人打出的牌)
     * @param seat 座位号
     * @param discardedTile 杠的牌
     * @param fromSeat 来源座位
     * @return 补摸的牌
     */
    MahjongTile mingKong(int seat, MahjongTile discardedTile, int fromSeat);

    /**
     * 暗杠(手中四张相同)
     * @param seat 座位号
     * @param tile 杠的牌
     * @return 补摸的牌
     */
    MahjongTile anKong(int seat, MahjongTile tile);

    /**
     * 补杠(已碰后摸到第四张)
     * @param seat 座位号
     * @param tile 杠的牌
     * @return 补摸的牌
     */
    MahjongTile buKong(int seat, MahjongTile tile);

    /**
     * 补花
     * @param seat 座位号
     * @return 补到的牌(可能还是花牌)
     */
    MahjongTile buHua(int seat);

    /**
     * 胡牌
     * @param seat 座位号
     * @return 胡牌结果
     */
    HuResult hu(int seat);

    // ==================== 特殊胡牌判定 ====================

    /**
     * 是否大吊车(吃碰杠4次后单吊)
     */
    boolean isDaDiaoChe(int seat);

    /**
     * 是否杠开(杠后补牌胡)
     */
    boolean isGangKai(int seat);

    /**
     * 是否跑百搭(任意牌都能胡)
     */
    boolean isPaoBaiDa(int seat);

    /**
     * 是否无百搭(手中无百搭)
     */
    boolean isWuBaiDa(int seat);

    /**
     * 是否门清(无吃碰明杠)
     */
    boolean isMenQing(int seat);

    /**
     * 是否四百搭(四个百搭直接胡)
     */
    boolean isSiBaiDa(int seat);

    // ==================== 游戏状态判定 ====================

    /**
     * 是否流局(荒番)
     * @return 是否流局
     */
    boolean isRoundDraw();

    /**
     * 计算得分
     * @param result 胡牌结果
     * @param flyCount 飞苍蝇数量
     * @param baseScore 底分
     * @param maxScore 封顶分数(null表示无封顶)
     * @return 各玩家分数变化 {seat -> scoreChange}
     */
    Map<Integer, Integer> calculateScore(HuResult result, int flyCount, int baseScore, Integer maxScore);

    /**
     * 获取下一个操作玩家座位(逆时针)
     * @param currentSeat 当前座位
     * @param playerCount 玩家人数
     * @return 下一个座位
     */
    int getNextSeat(int currentSeat, int playerCount);

    // ==================== 内部类定义 ====================

    /**
     * 明牌(碰/杠)信息
     */
    class Meld {
        private MeldType type;      // 类型
        private List<MahjongTile> tiles;  // 组成的牌
        private int fromSeat;       // 来源座位(0表示自己)
        private boolean isConcealed; // 是否暗杠

        public enum MeldType {
            CHI,        // 吃
            PONG,       // 碰
            MING_KONG,  // 明杠
            AN_KONG,    // 暗杠
            BU_KONG     // 补杠
        }

        public Meld() {}

        public Meld(MeldType type, List<MahjongTile> tiles, int fromSeat, boolean isConcealed) {
            this.type = type;
            this.tiles = tiles;
            this.fromSeat = fromSeat;
            this.isConcealed = isConcealed;
        }

        public MeldType getType() { return type; }
        public void setType(MeldType type) { this.type = type; }
        public List<MahjongTile> getTiles() { return tiles; }
        public void setTiles(List<MahjongTile> tiles) { this.tiles = tiles; }
        public int getFromSeat() { return fromSeat; }
        public void setFromSeat(int fromSeat) { this.fromSeat = fromSeat; }
        public boolean isConcealed() { return isConcealed; }
        public void setConcealed(boolean concealed) { isConcealed = concealed; }
    }

    /**
     * 胡牌结果
     */
    class HuResult {
        private int winnerSeat;     // 胡牌者座位
        private MahjongTile huTile; // 胡的牌
        private boolean isSelfDraw; // 是否自摸
        private List<MahjongHuType> huTypes; // 胡牌类型列表
        private int fanCount;       // 总番数
        private List<MahjongTile> winningHand; // 胡牌时的手牌

        public HuResult() {}

        public HuResult(int winnerSeat, MahjongTile huTile, boolean isSelfDraw,
                        List<MahjongHuType> huTypes, int fanCount, List<MahjongTile> winningHand) {
            this.winnerSeat = winnerSeat;
            this.huTile = huTile;
            this.isSelfDraw = isSelfDraw;
            this.huTypes = huTypes;
            this.fanCount = fanCount;
            this.winningHand = winningHand;
        }

        public int getWinnerSeat() { return winnerSeat; }
        public void setWinnerSeat(int winnerSeat) { this.winnerSeat = winnerSeat; }
        public MahjongTile getHuTile() { return huTile; }
        public void setHuTile(MahjongTile huTile) { this.huTile = huTile; }
        public boolean isSelfDraw() { return isSelfDraw; }
        public void setSelfDraw(boolean selfDraw) { isSelfDraw = selfDraw; }
        public List<MahjongHuType> getHuTypes() { return huTypes; }
        public void setHuTypes(List<MahjongHuType> huTypes) { this.huTypes = huTypes; }
        public int getFanCount() { return fanCount; }
        public void setFanCount(int fanCount) { this.fanCount = fanCount; }
        public List<MahjongTile> getWinningHand() { return winningHand; }
        public void setWinningHand(List<MahjongTile> winningHand) { this.winningHand = winningHand; }
    }

    /**
     * 骰子结果
     */
    class DiceResult {
        private int dice1;
        private int dice2;
        private int sum;

        public DiceResult(int dice1, int dice2) {
            this.dice1 = dice1;
            this.dice2 = dice2;
            this.sum = dice1 + dice2;
        }

        public int getDice1() { return dice1; }
        public int getDice2() { return dice2; }
        public int getSum() { return sum; }
        public int getSmaller() { return Math.min(dice1, dice2); }
    }
}
