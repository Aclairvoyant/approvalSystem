package com.approval.system.service.impl;

import com.approval.system.common.enums.MahjongActionType;
import com.approval.system.common.enums.MahjongHuType;
import com.approval.system.common.enums.MahjongTileType;
import com.approval.system.entity.MahjongRound;
import com.approval.system.entity.MahjongTile;
import com.approval.system.service.IMahjongEngine;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上海敲麻引擎实现
 * 规则特点:
 * 1. 只能自摸胡牌，不能点炮
 * 2. 可碰可杠，不能吃
 * 3. 144张牌(含8张花牌)
 * 4. 逆时针出牌顺序
 */
@Component("shanghaiQiaomaEngine")
public class ShanghaiQiaomaEngine implements IMahjongEngine {

    // 游戏状态
    protected MahjongRound currentRound;
    protected int playerCount;
    protected int flowerMode;

    // 牌墙
    protected List<MahjongTile> wall;

    // 玩家手牌 (seat -> tiles)
    protected Map<Integer, List<MahjongTile>> playerHands;

    // 玩家花牌
    protected Map<Integer, List<MahjongTile>> playerFlowers;

    // 玩家明牌(碰/杠)
    protected Map<Integer, List<Meld>> playerMelds;

    // 玩家弃牌
    protected Map<Integer, List<MahjongTile>> playerDiscards;

    // 最后操作信息
    protected MahjongTile lastDiscardedTile;
    protected int lastDiscardSeat;
    protected boolean lastActionWasKong; // 用于判断杠开
    protected MahjongTile lastDrawnTile; // 最后摸到的牌

    @Override
    public void initRound(MahjongRound round, int playerCount, int flowerMode) {
        this.currentRound = round;
        this.playerCount = playerCount;
        this.flowerMode = flowerMode;

        // 初始化容器
        this.wall = new ArrayList<>();
        this.playerHands = new HashMap<>();
        this.playerFlowers = new HashMap<>();
        this.playerMelds = new HashMap<>();
        this.playerDiscards = new HashMap<>();

        for (int seat = 1; seat <= 4; seat++) {
            playerHands.put(seat, new ArrayList<>());
            playerFlowers.put(seat, new ArrayList<>());
            playerMelds.put(seat, new ArrayList<>());
            playerDiscards.put(seat, new ArrayList<>());
        }

        this.lastDiscardedTile = null;
        this.lastDiscardSeat = 0;
        this.lastActionWasKong = false;
        this.lastDrawnTile = null;
    }

    @Override
    public List<MahjongTile> shuffleAndDeal(int dealerSeat) {
        // 1. 创建所有牌
        wall = createAllTiles();

        // 2. 洗牌
        Collections.shuffle(wall);

        // 3. 发牌 - 庄家14张，其他人13张
        for (int i = 0; i < playerCount; i++) {
            int seat = ((dealerSeat - 1 + i) % playerCount) + 1;
            int cardCount = (seat == dealerSeat) ? 14 : 13;

            for (int j = 0; j < cardCount; j++) {
                if (!wall.isEmpty()) {
                    MahjongTile tile = wall.remove(0);
                    addTileToHand(seat, tile);
                }
            }
        }

        // 4. 处理花牌 - 自动补花
        for (int seat = 1; seat <= playerCount; seat++) {
            autoReplaceFlowers(seat);
        }

        // 5. 排序手牌
        for (int seat = 1; seat <= playerCount; seat++) {
            sortHand(seat);
        }

        return wall;
    }

    /**
     * 创建所有麻将牌
     */
    protected List<MahjongTile> createAllTiles() {
        List<MahjongTile> tiles = new ArrayList<>();

        // 万筒条各36张 (1-9 各4张)
        for (MahjongTileType type : Arrays.asList(
                MahjongTileType.WAN, MahjongTileType.TONG, MahjongTileType.TIAO)) {
            for (int num = 1; num <= 9; num++) {
                for (int i = 0; i < 4; i++) {
                    tiles.add(new MahjongTile(type, num));
                }
            }
        }

        // 风牌16张 (东南西北各4张) - 根据花牌模式处理
        int fengCount = getFengTileCount();
        for (int num = 1; num <= 4; num++) { // 1=东,2=南,3=西,4=北
            for (int i = 0; i < fengCount; i++) {
                tiles.add(new MahjongTile(MahjongTileType.FENG, num));
            }
        }

        // 箭牌12张 (中发白各4张) - 根据花牌模式处理
        int jianCount = getJianTileCount();
        for (int num = 1; num <= 3; num++) { // 1=中,2=发,3=白
            for (int i = 0; i < jianCount; i++) {
                tiles.add(new MahjongTile(MahjongTileType.JIAN, num));
            }
        }

        // 花牌 - 根据花牌模式处理
        addFlowerTiles(tiles);

        return tiles;
    }

    /**
     * 获取风牌数量(每种)
     */
    protected int getFengTileCount() {
        if (flowerMode == 20) {
            return 1; // 20花模式: 每种风牌只留1张
        } else if (flowerMode == 36) {
            return 0; // 36花模式: 风牌全当花
        }
        return 4; // 8花模式: 正常4张
    }

    /**
     * 获取箭牌数量(每种)
     */
    protected int getJianTileCount() {
        if (flowerMode == 36) {
            return 0; // 36花模式: 箭牌全当花
        }
        return 4; // 其他模式: 正常4张
    }

    /**
     * 添加花牌
     */
    protected void addFlowerTiles(List<MahjongTile> tiles) {
        // 基础8张花牌 (春夏秋冬梅兰竹菊)
        for (int num = 1; num <= 8; num++) {
            tiles.add(new MahjongTile(MahjongTileType.HUA, num));
        }

        // 20花模式: 东南西北各3张当花 (编号9-20)
        if (flowerMode >= 20) {
            int huaNum = 9;
            for (int feng = 1; feng <= 4; feng++) {
                for (int i = 0; i < 3; i++) {
                    tiles.add(new MahjongTile(MahjongTileType.HUA, huaNum++));
                }
            }
        }

        // 36花模式: 额外添加风牌和箭牌作为花
        if (flowerMode == 36) {
            int huaNum = 21;
            // 剩余1张东南西北
            for (int feng = 1; feng <= 4; feng++) {
                tiles.add(new MahjongTile(MahjongTileType.HUA, huaNum++));
            }
            // 中发白12张
            for (int jian = 1; jian <= 3; jian++) {
                for (int i = 0; i < 4; i++) {
                    tiles.add(new MahjongTile(MahjongTileType.HUA, huaNum++));
                }
            }
        }
    }

    /**
     * 自动补花
     */
    protected void autoReplaceFlowers(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        List<MahjongTile> flowers = playerFlowers.get(seat);

        boolean hasFlower = true;
        while (hasFlower && !wall.isEmpty()) {
            hasFlower = false;

            // 找出手牌中的第一张花牌
            MahjongTile flowerTile = null;
            for (MahjongTile tile : hand) {
                if (tile.getType() == MahjongTileType.HUA) {
                    flowerTile = tile;
                    break;
                }
            }

            // 如果找到花牌，移除并补牌
            if (flowerTile != null) {
                hand.remove(flowerTile);
                flowers.add(flowerTile);

                // 补牌
                if (!wall.isEmpty()) {
                    MahjongTile newTile = wall.remove(wall.size() - 1); // 从牌墙尾部补
                    hand.add(newTile);
                    hasFlower = true; // 继续检查新补的牌是否是花牌
                }
            }
        }
    }

    /**
     * 添加牌到手牌
     */
    protected void addTileToHand(int seat, MahjongTile tile) {
        playerHands.get(seat).add(tile);
    }

    /**
     * 排序手牌
     */
    protected void sortHand(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        Collections.sort(hand);
    }

    @Override
    public List<MahjongTile> getPlayerHand(int seat) {
        return new ArrayList<>(playerHands.getOrDefault(seat, new ArrayList<>()));
    }

    @Override
    public List<MahjongTile> getPlayerFlowers(int seat) {
        return new ArrayList<>(playerFlowers.getOrDefault(seat, new ArrayList<>()));
    }

    @Override
    public List<Meld> getPlayerMelds(int seat) {
        return new ArrayList<>(playerMelds.getOrDefault(seat, new ArrayList<>()));
    }

    @Override
    public List<MahjongTile> getPlayerDiscards(int seat) {
        return new ArrayList<>(playerDiscards.getOrDefault(seat, new ArrayList<>()));
    }

    @Override
    public int getWallRemaining() {
        return wall != null ? wall.size() : 0;
    }

    @Override
    public List<MahjongTile> getWall() {
        return wall != null ? new ArrayList<>(wall) : new ArrayList<>();
    }

    @Override
    public void setWall(List<MahjongTile> wall) {
        this.wall = wall != null ? new ArrayList<>(wall) : new ArrayList<>();
    }

    @Override
    public boolean canDraw(int seat) {
        return wall != null && !wall.isEmpty();
    }

    @Override
    public boolean canDiscard(int seat, MahjongTile tile) {
        List<MahjongTile> hand = playerHands.get(seat);
        return hand != null && containsTile(hand, tile);
    }

    @Override
    public boolean canPong(int seat, MahjongTile discardedTile) {
        if (discardedTile == null) return false;
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return false;

        // 统计手牌中相同牌的数量
        long count = hand.stream()
                .filter(t -> t.equals(discardedTile))
                .count();
        return count >= 2;
    }

    @Override
    public List<List<String>> getChiOptions(int seat, MahjongTile discardedTile, int fromSeat) {
        // 敲麻不能吃牌
        return new ArrayList<>();
    }

    @Override
    public void chi(int seat, MahjongTile discardedTile, int fromSeat, List<MahjongTile> chiTiles) {
        // 敲麻不能吃牌，空实现
        throw new UnsupportedOperationException("敲麻规则不支持吃牌");
    }

    @Override
    public boolean canMingKong(int seat, MahjongTile discardedTile) {
        if (discardedTile == null) return false;
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return false;

        // 统计手牌中相同牌的数量(需要3张)
        long count = hand.stream()
                .filter(t -> t.equals(discardedTile))
                .count();
        return count >= 3;
    }

    @Override
    public List<MahjongTile> getAnKongOptions(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return new ArrayList<>();

        // 找出手牌中有4张相同的牌
        Map<String, Long> countMap = hand.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        return countMap.entrySet().stream()
                .filter(e -> e.getValue() >= 4)
                .map(e -> MahjongTile.fromCode(e.getKey()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MahjongTile> getBuKongOptions(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        List<Meld> melds = playerMelds.get(seat);
        if (hand == null || melds == null) return new ArrayList<>();

        List<MahjongTile> options = new ArrayList<>();

        // 找已碰的牌，且手牌中还有第4张
        for (Meld meld : melds) {
            if (meld.getType() == Meld.MeldType.PONG) {
                MahjongTile pongTile = meld.getTiles().get(0);
                if (containsTile(hand, pongTile)) {
                    options.add(pongTile);
                }
            }
        }

        return options;
    }

    @Override
    public HuResult canHu(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return null;

        // 检查是否满足胡牌条件
        if (!isWinningHand(hand, playerMelds.get(seat))) {
            return null;
        }

        // 计算胡牌类型和番数
        List<MahjongHuType> huTypes = calculateHuTypes(seat, hand);
        int fanCount = huTypes.stream().mapToInt(MahjongHuType::getFan).sum();

        HuResult result = new HuResult();
        result.setWinnerSeat(seat);
        result.setHuTile(lastDrawnTile);
        result.setSelfDraw(true); // 敲麻只能自摸
        result.setHuTypes(huTypes);
        result.setFanCount(fanCount);
        result.setWinningHand(new ArrayList<>(hand));

        return result;
    }

    @Override
    public List<MahjongActionType> getAvailableActions(int seat, MahjongTile discardedTile, boolean isMyTurn) {
        List<MahjongActionType> actions = new ArrayList<>();

        if (isMyTurn) {
            // 自己的回合
            List<MahjongTile> hand = playerHands.get(seat);

            // 检查是否可以胡牌
            if (canHu(seat) != null) {
                actions.add(MahjongActionType.HU);
            }

            // 检查是否可以暗杠
            if (!getAnKongOptions(seat).isEmpty()) {
                actions.add(MahjongActionType.AN_KONG);
            }

            // 检查是否可以补杠
            if (!getBuKongOptions(seat).isEmpty()) {
                actions.add(MahjongActionType.BU_KONG);
            }

            // 检查是否需要出牌
            // 手牌数量为 3n+2 时需要出牌（14, 11, 8, 5, 2）
            // 14张=正常摸牌后，11张=碰/杠后，8张=两次碰后，以此类推
            if (hand != null && !hand.isEmpty() && hand.size() % 3 == 2) {
                actions.add(MahjongActionType.DISCARD);
            }
        } else if (discardedTile != null) {
            // 别人出牌后
            // 检查碰
            if (canPong(seat, discardedTile)) {
                actions.add(MahjongActionType.PONG);
            }

            // 检查明杠
            if (canMingKong(seat, discardedTile)) {
                actions.add(MahjongActionType.MING_KONG);
            }

            // 可以过
            if (!actions.isEmpty()) {
                actions.add(MahjongActionType.PASS);
            }
        }

        return actions;
    }

    @Override
    public MahjongTile draw(int seat) {
        if (wall == null || wall.isEmpty()) return null;

        MahjongTile tile = wall.remove(0);
        if (tile == null) return null;  // 防止牌墙中有空元素

        playerHands.get(seat).add(tile);
        lastDrawnTile = tile;
        lastActionWasKong = false;

        // 如果摸到花牌，自动补花
        if (tile.getType() == MahjongTileType.HUA) {
            playerHands.get(seat).remove(tile);
            playerFlowers.get(seat).add(tile);
            return buHua(seat);
        }

        // 不排序手牌，让摸到的牌保持在最右边
        // sortHand(seat);
        return tile;
    }

    @Override
    public void discard(int seat, MahjongTile tile) {
        List<MahjongTile> hand = playerHands.get(seat);
        removeTileFromHand(hand, tile);
        playerDiscards.get(seat).add(tile);

        lastDiscardedTile = tile;
        lastDiscardSeat = seat;
        lastActionWasKong = false;

        // 出牌后对手牌排序
        sortHand(seat);
    }

    @Override
    public void pong(int seat, MahjongTile discardedTile, int fromSeat) {
        List<MahjongTile> hand = playerHands.get(seat);

        // 从手牌移除2张相同牌
        int removed = 0;
        Iterator<MahjongTile> it = hand.iterator();
        while (it.hasNext() && removed < 2) {
            if (it.next().equals(discardedTile)) {
                it.remove();
                removed++;
            }
        }

        // 创建碰牌
        List<MahjongTile> meldTiles = new ArrayList<>();
        meldTiles.add(discardedTile.copy());
        meldTiles.add(discardedTile.copy());
        meldTiles.add(discardedTile.copy());

        Meld meld = new Meld(Meld.MeldType.PONG, meldTiles, fromSeat, false);
        playerMelds.get(seat).add(meld);

        // 从出牌者弃牌中移除
        List<MahjongTile> discards = playerDiscards.get(fromSeat);
        if (!discards.isEmpty()) {
            discards.remove(discards.size() - 1);
        }

        lastActionWasKong = false;
    }

    @Override
    public MahjongTile mingKong(int seat, MahjongTile discardedTile, int fromSeat) {
        List<MahjongTile> hand = playerHands.get(seat);

        // 从手牌移除3张相同牌
        int removed = 0;
        Iterator<MahjongTile> it = hand.iterator();
        while (it.hasNext() && removed < 3) {
            if (it.next().equals(discardedTile)) {
                it.remove();
                removed++;
            }
        }

        // 创建杠牌
        List<MahjongTile> meldTiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            meldTiles.add(discardedTile.copy());
        }

        Meld meld = new Meld(Meld.MeldType.MING_KONG, meldTiles, fromSeat, false);
        playerMelds.get(seat).add(meld);

        // 从出牌者弃牌中移除
        List<MahjongTile> discards = playerDiscards.get(fromSeat);
        if (!discards.isEmpty()) {
            discards.remove(discards.size() - 1);
        }

        lastActionWasKong = true;

        // 补牌(从牌墙尾部)
        if (!wall.isEmpty()) {
            MahjongTile newTile = wall.remove(wall.size() - 1);
            hand.add(newTile);
            lastDrawnTile = newTile;

            if (newTile.getType() == MahjongTileType.HUA) {
                hand.remove(newTile);
                playerFlowers.get(seat).add(newTile);
                return buHua(seat);
            }

            // 不排序，让补到的牌显示在最右边
            // sortHand(seat);
            return newTile;
        }

        return null;
    }

    @Override
    public MahjongTile anKong(int seat, MahjongTile tile) {
        List<MahjongTile> hand = playerHands.get(seat);

        // 从手牌移除4张相同牌
        int removed = 0;
        Iterator<MahjongTile> it = hand.iterator();
        while (it.hasNext() && removed < 4) {
            if (it.next().equals(tile)) {
                it.remove();
                removed++;
            }
        }

        // 创建暗杠
        List<MahjongTile> meldTiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            meldTiles.add(tile.copy());
        }

        Meld meld = new Meld(Meld.MeldType.AN_KONG, meldTiles, 0, true);
        playerMelds.get(seat).add(meld);

        lastActionWasKong = true;

        // 补牌
        if (!wall.isEmpty()) {
            MahjongTile newTile = wall.remove(wall.size() - 1);
            hand.add(newTile);
            lastDrawnTile = newTile;

            if (newTile.getType() == MahjongTileType.HUA) {
                hand.remove(newTile);
                playerFlowers.get(seat).add(newTile);
                return buHua(seat);
            }

            // 不排序，让补到的牌显示在最右边
            // sortHand(seat);
            return newTile;
        }

        return null;
    }

    @Override
    public MahjongTile buKong(int seat, MahjongTile tile) {
        List<MahjongTile> hand = playerHands.get(seat);
        List<Meld> melds = playerMelds.get(seat);

        // 从手牌移除1张
        removeTileFromHand(hand, tile);

        // 找到对应的碰牌并升级为补杠
        for (Meld meld : melds) {
            if (meld.getType() == Meld.MeldType.PONG
                    && meld.getTiles().get(0).equals(tile)) {
                meld.setType(Meld.MeldType.BU_KONG);
                meld.getTiles().add(tile.copy());
                break;
            }
        }

        lastActionWasKong = true;

        // 补牌
        if (!wall.isEmpty()) {
            MahjongTile newTile = wall.remove(wall.size() - 1);
            hand.add(newTile);
            lastDrawnTile = newTile;

            if (newTile.getType() == MahjongTileType.HUA) {
                hand.remove(newTile);
                playerFlowers.get(seat).add(newTile);
                return buHua(seat);
            }

            // 不排序，让补到的牌显示在最右边
            // sortHand(seat);
            return newTile;
        }

        return null;
    }

    @Override
    public MahjongTile buHua(int seat) {
        if (wall.isEmpty()) return null;

        MahjongTile tile = wall.remove(wall.size() - 1);

        if (tile.getType() == MahjongTileType.HUA) {
            playerFlowers.get(seat).add(tile);
            return buHua(seat); // 递归补花
        }

        playerHands.get(seat).add(tile);
        lastDrawnTile = tile;
        // 不排序，让补到的牌显示在最右边
        // sortHand(seat);
        return tile;
    }

    @Override
    public HuResult hu(int seat) {
        HuResult result = canHu(seat);
        if (result == null) {
            throw new IllegalStateException("不能胡牌");
        }
        return result;
    }

    // ==================== 特殊胡牌判定 ====================

    @Override
    public boolean isDaDiaoChe(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        List<Meld> melds = playerMelds.get(seat);

        // 吃碰杠4次后单吊 = 手牌只剩1张
        return melds != null && melds.size() == 4 && hand != null && hand.size() == 2;
    }

    @Override
    public boolean isGangKai(int seat) {
        return lastActionWasKong;
    }

    @Override
    public boolean isPaoBaiDa(int seat) {
        // 基础敲麻无百搭，子类实现
        return false;
    }

    @Override
    public boolean isWuBaiDa(int seat) {
        // 基础敲麻无百搭，子类实现
        return true;
    }

    @Override
    public boolean isMenQing(int seat) {
        List<Meld> melds = playerMelds.get(seat);
        if (melds == null || melds.isEmpty()) {
            return true;
        }

        // 检查是否全是暗杠
        return melds.stream().allMatch(m -> m.getType() == Meld.MeldType.AN_KONG);
    }

    @Override
    public boolean isSiBaiDa(int seat) {
        // 基础敲麻无百搭，子类实现
        return false;
    }

    @Override
    public boolean isRoundDraw() {
        return wall.isEmpty();
    }

    @Override
    public Map<Integer, Integer> calculateScore(HuResult result, int flyCount, int baseScore, Integer maxScore) {
        Map<Integer, Integer> scoreChanges = new HashMap<>();

        if (result == null) {
            return scoreChanges;
        }

        int winnerSeat = result.getWinnerSeat();
        int fanCount = result.getFanCount();

        // 加上飞苍蝇
        fanCount += flyCount;

        // 计算得分
        int score = baseScore * (int) Math.pow(2, fanCount - 1);

        // 封顶
        if (maxScore != null && score > maxScore) {
            score = maxScore;
        }

        // 自摸胡: 其他所有人付分
        int totalGain = 0;
        for (int seat = 1; seat <= playerCount; seat++) {
            if (seat != winnerSeat) {
                scoreChanges.put(seat, -score);
                totalGain += score;
            }
        }
        scoreChanges.put(winnerSeat, totalGain);

        return scoreChanges;
    }

    @Override
    public int getNextSeat(int currentSeat, int playerCount) {
        // 逆时针: 1 -> 4 -> 3 -> 2 -> 1 (4人)
        int next = currentSeat - 1;
        if (next < 1) {
            next = playerCount;
        }
        return next;
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查手牌是否包含指定牌
     */
    protected boolean containsTile(List<MahjongTile> tiles, MahjongTile target) {
        return tiles.stream().anyMatch(t -> t.equals(target));
    }

    /**
     * 从手牌移除一张指定牌
     */
    protected void removeTileFromHand(List<MahjongTile> hand, MahjongTile tile) {
        Iterator<MahjongTile> it = hand.iterator();
        while (it.hasNext()) {
            if (it.next().equals(tile)) {
                it.remove();
                break;
            }
        }
    }

    /**
     * 判断是否是胡牌牌型
     */
    protected boolean isWinningHand(List<MahjongTile> hand, List<Meld> melds) {
        // 计算手牌+明牌组成的完整牌型
        int meldCount = (melds != null) ? melds.size() : 0;
        int needGroups = 4 - meldCount; // 还需要多少组

        // 尝试所有可能的雀头
        Map<String, Long> countMap = hand.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            if (entry.getValue() >= 2) {
                // 假设这张牌作为雀头
                List<MahjongTile> remaining = new ArrayList<>(hand);
                MahjongTile pair = MahjongTile.fromCode(entry.getKey());
                removeTileFromHand(remaining, pair);
                removeTileFromHand(remaining, pair);

                // 检查剩余牌是否能组成 needGroups 组
                if (canFormGroups(remaining, needGroups)) {
                    return true;
                }
            }
        }

        // 检查七对
        if (meldCount == 0 && hand.size() == 14) {
            if (isSevenPairs(hand)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查牌是否能组成指定数量的组(刻子或顺子)
     * 注: 上海敲麻不能吃牌，但胡牌时可以用顺子
     */
    protected boolean canFormGroups(List<MahjongTile> tiles, int groupCount) {
        if (groupCount == 0) {
            return tiles.isEmpty();
        }
        if (tiles.isEmpty()) {
            return false;
        }

        // 按牌排序
        List<MahjongTile> sorted = new ArrayList<>(tiles);
        Collections.sort(sorted);

        MahjongTile first = sorted.get(0);

        // 尝试组成刻子
        long sameCount = sorted.stream().filter(t -> t.equals(first)).count();
        if (sameCount >= 3) {
            List<MahjongTile> remaining = new ArrayList<>(sorted);
            for (int i = 0; i < 3; i++) {
                removeTileFromHand(remaining, first);
            }
            if (canFormGroups(remaining, groupCount - 1)) {
                return true;
            }
        }

        // 尝试组成顺子（只有数牌可以）
        if (first.isNumberTile() && first.getNumber() <= 7) {
            MahjongTile second = new MahjongTile(first.getType(), first.getNumber() + 1);
            MahjongTile third = new MahjongTile(first.getType(), first.getNumber() + 2);

            if (containsTile(sorted, second) && containsTile(sorted, third)) {
                List<MahjongTile> remaining = new ArrayList<>(sorted);
                removeTileFromHand(remaining, first);
                removeTileFromHand(remaining, second);
                removeTileFromHand(remaining, third);
                if (canFormGroups(remaining, groupCount - 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查是否是七对
     */
    protected boolean isSevenPairs(List<MahjongTile> hand) {
        if (hand.size() != 14) return false;

        Map<String, Long> countMap = hand.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        // 每种牌必须是2张或4张
        return countMap.values().stream().allMatch(c -> c == 2 || c == 4)
                && countMap.size() == 7;
    }

    /**
     * 计算胡牌类型
     */
    protected List<MahjongHuType> calculateHuTypes(int seat, List<MahjongTile> hand) {
        List<MahjongHuType> types = new ArrayList<>();

        // 基础胡
        types.add(MahjongHuType.NORMAL);

        // 大吊车
        if (isDaDiaoChe(seat)) {
            types.add(MahjongHuType.DA_DIAO_CHE);
        }

        // 杠开
        if (isGangKai(seat)) {
            types.add(MahjongHuType.GANG_KAI);
        }

        // 门清
        if (isMenQing(seat)) {
            types.add(MahjongHuType.MEN_QING);
        }

        // 检查清一色
        if (isQingYiSe(hand)) {
            types.add(MahjongHuType.QING_YI_SE);
        }

        // 检查对对胡
        if (isDuiDuiHu(hand, playerMelds.get(seat))) {
            types.add(MahjongHuType.DUI_DUI_HU);
        }

        // 检查七对
        if (isSevenPairs(hand) && playerMelds.get(seat).isEmpty()) {
            types.add(MahjongHuType.QI_DUI);
        }

        return types;
    }

    /**
     * 检查是否清一色
     */
    protected boolean isQingYiSe(List<MahjongTile> hand) {
        Set<MahjongTileType> types = hand.stream()
                .map(MahjongTile::getType)
                .filter(t -> t != MahjongTileType.HUA)
                .collect(Collectors.toSet());

        return types.size() == 1 &&
                (types.contains(MahjongTileType.WAN) ||
                 types.contains(MahjongTileType.TONG) ||
                 types.contains(MahjongTileType.TIAO));
    }

    /**
     * 检查是否对对胡(碰碰胡)
     */
    protected boolean isDuiDuiHu(List<MahjongTile> hand, List<Meld> melds) {
        // 所有明牌必须是碰或杠
        if (melds != null) {
            for (Meld meld : melds) {
                if (meld.getType() != Meld.MeldType.PONG &&
                    meld.getType() != Meld.MeldType.MING_KONG &&
                    meld.getType() != Meld.MeldType.AN_KONG &&
                    meld.getType() != Meld.MeldType.BU_KONG) {
                    return false;
                }
            }
        }

        // 手牌也必须全是刻子+雀头
        Map<String, Long> countMap = hand.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        int pairCount = 0;
        for (Long count : countMap.values()) {
            if (count == 2) {
                pairCount++;
            } else if (count != 3 && count != 4) {
                return false;
            }
        }

        return pairCount == 1;
    }
}
