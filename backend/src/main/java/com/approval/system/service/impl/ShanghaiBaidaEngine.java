package com.approval.system.service.impl;

import com.approval.system.common.enums.MahjongActionType;
import com.approval.system.common.enums.MahjongHuType;
import com.approval.system.common.enums.MahjongTileType;
import com.approval.system.entity.MahjongRound;
import com.approval.system.entity.MahjongTile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 上海百搭引擎实现
 * 继承敲麻引擎，添加百搭牌支持
 * 规则特点:
 * 1. 通过掷骰子确定百搭牌
 * 2. 百搭牌可代替除花以外的所有牌
 * 3. 打出的百搭牌作为普通牌
 * 4. 特殊胡牌: 跑百搭、无百搭、四百搭
 */
@Component("shanghaiBaidaEngine")
public class ShanghaiBaidaEngine extends ShanghaiQiaomaEngine {

    // 百搭牌
    private MahjongTile wildTile;

    // 前端引导牌
    private MahjongTile guideTile;

    // 骰子结果
    private DiceResult diceResult;

    // 玩家手中的百搭牌数量
    private Map<Integer, Integer> wildTileCount;

    @Override
    public void initRound(MahjongRound round, int playerCount, int flowerMode) {
        super.initRound(round, playerCount, flowerMode);
        this.wildTile = null;
        this.guideTile = null;
        this.diceResult = null;
        this.wildTileCount = new HashMap<>();
        for (int seat = 1; seat <= 4; seat++) {
            wildTileCount.put(seat, 0);
        }
    }

    /**
     * 掷骰子确定百搭牌
     * @param dealerSeat 庄家座位
     * @return 骰子结果
     */
    public DiceResult rollDice(int dealerSeat) {
        Random random = new Random();
        int dice1 = random.nextInt(6) + 1;
        int dice2 = random.nextInt(6) + 1;
        this.diceResult = new DiceResult(dice1, dice2);
        return diceResult;
    }

    /**
     * 计算牌墙起始座位
     * @param dice1 第一个骰子
     * @param dice2 第二个骰子
     * @param dealerSeat 庄家座位
     * @return 起始座位
     */
    public int calculateWallStartSeat(int dice1, int dice2, int dealerSeat) {
        int sum = dice1 + dice2;
        // 逆时针数: 庄家为1,逆时针2,3,4...
        return ((dealerSeat - 1 + sum - 1) % playerCount) + 1;
    }

    /**
     * 翻前端引导牌
     * @param wallStartSeat 牌墙起始座位
     * @param smallerDice 较小的骰子点数
     * @return 引导牌
     */
    public MahjongTile flipGuideTile(int wallStartSeat, int smallerDice) {
        // 在起始座位的下家牌墙上，取较小骰子数位置的牌
        // 这里简化处理：从牌墙中随机取一张作为引导牌
        if (wall.isEmpty()) return null;

        int position = Math.min(smallerDice - 1, wall.size() - 1);
        this.guideTile = wall.get(position);
        return guideTile;
    }

    /**
     * 根据引导牌确定百搭牌
     * @param guideTile 前端引导牌
     * @return 百搭牌
     */
    public MahjongTile determineWildTile(MahjongTile guideTile) {
        if (guideTile == null) return null;

        this.guideTile = guideTile;
        this.wildTile = guideTile.getNextTile();

        // 标记所有百搭牌
        markWildTiles();

        return wildTile;
    }

    /**
     * 标记牌墙和手牌中的百搭牌
     */
    private void markWildTiles() {
        if (wildTile == null) return;

        // 标记牌墙中的百搭
        for (MahjongTile tile : wall) {
            if (tile.getType() == wildTile.getType()
                    && tile.getNumber() == wildTile.getNumber()) {
                tile.setWild(true);
            }
        }

        // 标记手牌中的百搭
        for (int seat = 1; seat <= playerCount; seat++) {
            List<MahjongTile> hand = playerHands.get(seat);
            if (hand != null) {
                int count = 0;
                for (MahjongTile tile : hand) {
                    if (tile.getType() == wildTile.getType()
                            && tile.getNumber() == wildTile.getNumber()) {
                        tile.setWild(true);
                        count++;
                    }
                }
                wildTileCount.put(seat, count);
            }
        }
    }

    /**
     * 完整的发牌流程（含掷骰子定百搭）
     */
    @Override
    public List<MahjongTile> shuffleAndDeal(int dealerSeat) {
        // 先执行父类的发牌
        List<MahjongTile> remaining = super.shuffleAndDeal(dealerSeat);

        // 掷骰子
        rollDice(dealerSeat);

        // 计算起始座位
        int wallStartSeat = calculateWallStartSeat(diceResult.getDice1(),
                diceResult.getDice2(), dealerSeat);

        // 翻引导牌
        flipGuideTile(wallStartSeat, diceResult.getSmaller());

        // 确定百搭牌
        if (guideTile != null) {
            determineWildTile(guideTile);
        }

        // 检查四百搭情况
        for (int seat = 1; seat <= playerCount; seat++) {
            if (isSiBaiDa(seat)) {
                // 四百搭直接胡牌，这里只标记，实际处理由服务层完成
            }
        }

        return remaining;
    }

    @Override
    public MahjongTile draw(int seat) {
        MahjongTile tile = super.draw(seat);
        if (tile != null && isWildTile(tile)) {
            tile.setWild(true);
            wildTileCount.merge(seat, 1, Integer::sum);
        }
        return tile;
    }

    @Override
    public void discard(int seat, MahjongTile tile) {
        // 打出的百搭牌变成普通牌
        if (tile.isWild()) {
            tile.setWild(false);
            wildTileCount.merge(seat, -1, Integer::sum);
        }
        super.discard(seat, tile);
    }

    /**
     * 判断是否是百搭牌
     */
    public boolean isWildTile(MahjongTile tile) {
        if (wildTile == null || tile == null) return false;
        return tile.getType() == wildTile.getType()
                && tile.getNumber() == wildTile.getNumber();
    }

    /**
     * 获取百搭牌
     */
    public MahjongTile getWildTile() {
        return wildTile;
    }

    /**
     * 获取引导牌
     */
    public MahjongTile getGuideTile() {
        return guideTile;
    }

    /**
     * 获取骰子结果
     */
    public DiceResult getDiceResult() {
        return diceResult;
    }

    /**
     * 设置并恢复百搭牌状态（用于状态恢复）
     * @param wildTileCode 百搭牌编码
     * @param guideTileCode 引导牌编码
     */
    public void restoreWildTile(String wildTileCode, String guideTileCode) {
        if (wildTileCode != null && !wildTileCode.isEmpty()) {
            this.wildTile = MahjongTile.fromCode(wildTileCode);
        }
        if (guideTileCode != null && !guideTileCode.isEmpty()) {
            this.guideTile = MahjongTile.fromCode(guideTileCode);
        }
        // 重新标记所有百搭牌
        if (this.wildTile != null) {
            markWildTiles();
        }
    }

    /**
     * 获取玩家手中百搭牌数量
     */
    public int getWildTileCount(int seat) {
        return wildTileCount.getOrDefault(seat, 0);
    }

    // ==================== 重写胡牌判定(支持百搭) ====================

    @Override
    public HuResult canHu(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return null;

        // 检查四百搭(立即胡)
        if (isSiBaiDa(seat)) {
            return createHuResult(seat, MahjongHuType.SI_BAI_DA);
        }

        // 使用百搭牌辅助判断胡牌
        if (!isWinningHandWithWild(hand, playerMelds.get(seat))) {
            return null;
        }

        // 计算胡牌类型和番数
        List<MahjongHuType> huTypes = calculateHuTypesWithWild(seat, hand);
        int fanCount = huTypes.stream().mapToInt(MahjongHuType::getFan).sum();

        HuResult result = new HuResult();
        result.setWinnerSeat(seat);
        result.setHuTile(lastDrawnTile);
        result.setSelfDraw(true);
        result.setHuTypes(huTypes);
        result.setFanCount(fanCount);
        result.setWinningHand(new ArrayList<>(hand));

        return result;
    }

    /**
     * 创建胡牌结果
     */
    private HuResult createHuResult(int seat, MahjongHuType huType) {
        List<MahjongHuType> types = new ArrayList<>();
        types.add(huType);

        HuResult result = new HuResult();
        result.setWinnerSeat(seat);
        result.setHuTile(lastDrawnTile);
        result.setSelfDraw(true);
        result.setHuTypes(types);
        result.setFanCount(huType.getFan());
        result.setWinningHand(new ArrayList<>(playerHands.get(seat)));

        return result;
    }

    /**
     * 判断是否胡牌(支持百搭)
     */
    protected boolean isWinningHandWithWild(List<MahjongTile> hand, List<Meld> melds) {
        // 分离普通牌和百搭牌
        List<MahjongTile> normalTiles = new ArrayList<>();
        int wildCount = 0;

        for (MahjongTile tile : hand) {
            if (tile.isWild()) {
                wildCount++;
            } else {
                normalTiles.add(tile);
            }
        }

        int meldCount = (melds != null) ? melds.size() : 0;
        int needGroups = 4 - meldCount;

        // 使用百搭尝试组成胡牌
        return tryWinWithWild(normalTiles, wildCount, needGroups);
    }

    /**
     * 尝试用百搭组成胡牌
     */
    private boolean tryWinWithWild(List<MahjongTile> normalTiles, int wildCount, int needGroups) {
        // 获取所有可能的牌型
        Map<String, Long> countMap = normalTiles.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        // 尝试每种牌作为雀头
        Set<String> allTileCodes = new HashSet<>(countMap.keySet());
        // 也考虑用百搭组成雀头
        addPotentialTileCodes(allTileCodes);

        for (String pairCode : allTileCodes) {
            Map<String, Long> remaining = new HashMap<>(countMap);
            int usedWild = 0;

            // 尝试组成雀头
            Long pairCount = remaining.getOrDefault(pairCode, 0L);
            if (pairCount >= 2) {
                remaining.put(pairCode, pairCount - 2);
            } else if (pairCount == 1 && wildCount >= 1) {
                remaining.put(pairCode, 0L);
                usedWild = 1;
            } else if (pairCount == 0 && wildCount >= 2) {
                usedWild = 2;
            } else {
                continue;
            }

            // 尝试用剩余牌+百搭组成 needGroups 组
            if (tryFormGroupsWithWild(remaining, wildCount - usedWild, needGroups)) {
                return true;
            }
        }

        // 检查七对(含百搭)
        if (needGroups == 4 && normalTiles.size() + wildCount == 14) {
            if (isSevenPairsWithWild(normalTiles, wildCount)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 添加潜在的牌型代码(用于百搭匹配)
     */
    private void addPotentialTileCodes(Set<String> codes) {
        // 添加所有可能的万筒条
        for (MahjongTileType type : Arrays.asList(
                MahjongTileType.WAN, MahjongTileType.TONG, MahjongTileType.TIAO)) {
            for (int num = 1; num <= 9; num++) {
                codes.add(num + type.name());
            }
        }
        // 添加风牌
        for (int num = 1; num <= 4; num++) {
            codes.add(num + "FENG");
        }
        // 添加箭牌
        for (int num = 1; num <= 3; num++) {
            codes.add(num + "JIAN");
        }
    }

    /**
     * 尝试用百搭组成指定数量的组（支持刻子和顺子）
     */
    private boolean tryFormGroupsWithWild(Map<String, Long> countMap, int wildCount, int needGroups) {
        if (needGroups == 0) {
            // 检查是否还有剩余牌
            long remaining = countMap.values().stream().mapToLong(Long::longValue).sum();
            return remaining == 0;
        }

        // 清理count为0的项
        countMap = countMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (countMap.isEmpty()) {
            // 没有普通牌了，尝试用纯百搭组成剩余的组
            return wildCount >= needGroups * 3;
        }

        // 找出第一张牌进行尝试
        String firstTileCode = countMap.keySet().iterator().next();
        MahjongTile firstTile = MahjongTile.fromCode(firstTileCode);
        long firstCount = countMap.get(firstTileCode);

        // 尝试1: 组成刻子
        if (firstCount >= 3) {
            Map<String, Long> newMap = new HashMap<>(countMap);
            newMap.put(firstTileCode, firstCount - 3);
            if (tryFormGroupsWithWild(newMap, wildCount, needGroups - 1)) {
                return true;
            }
        }

        // 尝试2: 用百搭补成刻子
        if (firstCount >= 2 && wildCount >= 1) {
            Map<String, Long> newMap = new HashMap<>(countMap);
            newMap.put(firstTileCode, firstCount - 2);
            if (tryFormGroupsWithWild(newMap, wildCount - 1, needGroups - 1)) {
                return true;
            }
        }
        if (firstCount >= 1 && wildCount >= 2) {
            Map<String, Long> newMap = new HashMap<>(countMap);
            newMap.put(firstTileCode, firstCount - 1);
            if (tryFormGroupsWithWild(newMap, wildCount - 2, needGroups - 1)) {
                return true;
            }
        }

        // 尝试3: 组成顺子（只有数牌可以组成顺子）
        if (firstTile != null && firstTile.isNumberTile() && firstTile.getNumber() <= 7) {
            MahjongTileType type = firstTile.getType();
            int num = firstTile.getNumber();

            String code1 = firstTileCode;
            String code2 = (num + 1) + type.name();
            String code3 = (num + 2) + type.name();

            long count1 = countMap.getOrDefault(code1, 0L);
            long count2 = countMap.getOrDefault(code2, 0L);
            long count3 = countMap.getOrDefault(code3, 0L);

            // 完整顺子
            if (count1 >= 1 && count2 >= 1 && count3 >= 1) {
                Map<String, Long> newMap = new HashMap<>(countMap);
                newMap.put(code1, count1 - 1);
                newMap.put(code2, count2 - 1);
                newMap.put(code3, count3 - 1);
                if (tryFormGroupsWithWild(newMap, wildCount, needGroups - 1)) {
                    return true;
                }
            }

            // 用1张百搭补顺子
            if (wildCount >= 1) {
                // 缺第三张
                if (count1 >= 1 && count2 >= 1 && count3 == 0) {
                    Map<String, Long> newMap = new HashMap<>(countMap);
                    newMap.put(code1, count1 - 1);
                    newMap.put(code2, count2 - 1);
                    if (tryFormGroupsWithWild(newMap, wildCount - 1, needGroups - 1)) {
                        return true;
                    }
                }
                // 缺第二张
                if (count1 >= 1 && count2 == 0 && count3 >= 1) {
                    Map<String, Long> newMap = new HashMap<>(countMap);
                    newMap.put(code1, count1 - 1);
                    newMap.put(code3, count3 - 1);
                    if (tryFormGroupsWithWild(newMap, wildCount - 1, needGroups - 1)) {
                        return true;
                    }
                }
                // 缺第一张
                if (count1 == 0 && count2 >= 1 && count3 >= 1) {
                    Map<String, Long> newMap = new HashMap<>(countMap);
                    newMap.put(code2, count2 - 1);
                    newMap.put(code3, count3 - 1);
                    if (tryFormGroupsWithWild(newMap, wildCount - 1, needGroups - 1)) {
                        return true;
                    }
                }
            }

            // 用2张百搭补顺子
            if (wildCount >= 2) {
                if (count1 >= 1) {
                    Map<String, Long> newMap = new HashMap<>(countMap);
                    newMap.put(code1, count1 - 1);
                    if (tryFormGroupsWithWild(newMap, wildCount - 2, needGroups - 1)) {
                        return true;
                    }
                }
            }
        }

        // 尝试4: 纯百搭组成一组
        if (wildCount >= 3) {
            if (tryFormGroupsWithWild(countMap, wildCount - 3, needGroups - 1)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查七对(含百搭)
     */
    private boolean isSevenPairsWithWild(List<MahjongTile> normalTiles, int wildCount) {
        Map<String, Long> countMap = normalTiles.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        int neededWild = 0;
        int pairs = 0;

        for (Long count : countMap.values()) {
            if (count % 2 == 1) {
                neededWild++; // 需要一张百搭配对
            }
            pairs += count / 2;
        }

        // 剩余的百搭可以两两成对
        int remainingWild = wildCount - neededWild;
        if (remainingWild < 0) return false;

        pairs += remainingWild / 2;

        return pairs == 7;
    }

    /**
     * 计算胡牌类型(支持百搭)
     */
    protected List<MahjongHuType> calculateHuTypesWithWild(int seat, List<MahjongTile> hand) {
        List<MahjongHuType> types = new ArrayList<>();

        // 基础胡
        types.add(MahjongHuType.NORMAL);

        // 四百搭
        if (isSiBaiDa(seat)) {
            types.clear();
            types.add(MahjongHuType.SI_BAI_DA);
            return types;
        }

        // 无百搭
        if (isWuBaiDa(seat)) {
            types.add(MahjongHuType.WU_BAI_DA);
        }

        // 跑百搭
        if (isPaoBaiDa(seat)) {
            types.add(MahjongHuType.PAO_BAI_DA);
        }

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

        // 清一色
        List<MahjongTile> nonWildTiles = hand.stream()
                .filter(t -> !t.isWild())
                .collect(Collectors.toList());
        if (isQingYiSe(nonWildTiles)) {
            types.add(MahjongHuType.QING_YI_SE);
        }

        // 对对胡
        if (isDuiDuiHuWithWild(hand, playerMelds.get(seat))) {
            types.add(MahjongHuType.DUI_DUI_HU);
        }

        // 七对
        int wildInHand = (int) hand.stream().filter(MahjongTile::isWild).count();
        List<MahjongTile> normalInHand = hand.stream()
                .filter(t -> !t.isWild())
                .collect(Collectors.toList());
        if (playerMelds.get(seat).isEmpty() && isSevenPairsWithWild(normalInHand, wildInHand)) {
            types.add(MahjongHuType.QI_DUI);
        }

        return types;
    }

    /**
     * 检查对对胡(支持百搭)
     */
    private boolean isDuiDuiHuWithWild(List<MahjongTile> hand, List<Meld> melds) {
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

        // 手牌分离百搭和普通牌
        List<MahjongTile> normalTiles = new ArrayList<>();
        int wildCount = 0;
        for (MahjongTile tile : hand) {
            if (tile.isWild()) {
                wildCount++;
            } else {
                normalTiles.add(tile);
            }
        }

        // 检查是否能组成 刻子+雀头
        Map<String, Long> countMap = normalTiles.stream()
                .collect(Collectors.groupingBy(MahjongTile::toCode, Collectors.counting()));

        // 尝试各种组合
        return tryDuiDuiHu(countMap, wildCount);
    }

    /**
     * 尝试组成对对胡
     */
    private boolean tryDuiDuiHu(Map<String, Long> countMap, int wildCount) {
        // 计算需要多少百搭来补成刻子
        int needed = 0;
        int pairs = 0;

        for (Long count : countMap.values()) {
            int remainder = (int) (count % 3);
            if (remainder == 1) {
                needed += 2; // 需要2张百搭补成刻子
            } else if (remainder == 2) {
                // 可以作为雀头或需要1张百搭补成刻子
                if (pairs == 0) {
                    pairs = 1; // 作为雀头
                } else {
                    needed += 1; // 需要1张百搭补成刻子
                }
            }
        }

        // 如果没有找到雀头，需要用百搭
        if (pairs == 0) {
            needed += 2;
        }

        return needed <= wildCount;
    }

    // ==================== 特殊胡牌判定(百搭版) ====================

    @Override
    public boolean isPaoBaiDa(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return false;

        // 跑百搭: 听全牌(任意牌都能胡)
        // 简化判断: 手牌中百搭牌数量足够多，使得能听多张牌
        int wildInHand = (int) hand.stream().filter(MahjongTile::isWild).count();

        // 需要更复杂的听牌分析，这里简化为百搭数>=3
        return wildInHand >= 3;
    }

    @Override
    public boolean isWuBaiDa(int seat) {
        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return false;

        // 无百搭: 手中无百搭牌
        return hand.stream().noneMatch(MahjongTile::isWild);
    }

    @Override
    public boolean isSiBaiDa(int seat) {
        // 四百搭: 手中有4张百搭牌
        return getWildTileCount(seat) >= 4;
    }

    // ==================== 碰杠规则(百搭不能碰杠) ====================

    // ==================== 吃牌规则(百搭麻将可以吃上家的牌) ====================

    /**
     * 判断是否是上家
     * 逆时针顺序: 1->4->3->2->1
     * seat的上家是 (seat % playerCount) + 1
     */
    private boolean isUpperSeat(int seat, int fromSeat) {
        // 逆时针：1的上家是2，2的上家是3，3的上家是4，4的上家是1
        int upperSeat = (seat % playerCount) + 1;
        return fromSeat == upperSeat;
    }

    @Override
    public List<List<String>> getChiOptions(int seat, MahjongTile discardedTile, int fromSeat) {
        List<List<String>> options = new ArrayList<>();

        // 只能吃上家的牌
        if (!isUpperSeat(seat, fromSeat)) {
            return options;
        }

        // 只有数牌可以吃
        if (discardedTile == null || !discardedTile.isNumberTile()) {
            return options;
        }

        List<MahjongTile> hand = playerHands.get(seat);
        if (hand == null) return options;

        MahjongTileType type = discardedTile.getType();
        int num = discardedTile.getNumber();

        // 检查三种吃法：
        // 1. 吃的牌在左边（如吃3，手中有4,5）: num+1, num+2
        // 2. 吃的牌在中间（如吃4，手中有3,5）: num-1, num+1
        // 3. 吃的牌在右边（如吃5，手中有3,4）: num-2, num-1

        // 检查吃法1: 吃的牌在左边
        if (num <= 7) {
            MahjongTile tile1 = new MahjongTile(type, num + 1);
            MahjongTile tile2 = new MahjongTile(type, num + 2);
            if (containsTile(hand, tile1) && containsTile(hand, tile2)) {
                List<String> option = Arrays.asList(tile1.toCode(), tile2.toCode());
                options.add(option);
            }
        }

        // 检查吃法2: 吃的牌在中间
        if (num >= 2 && num <= 8) {
            MahjongTile tile1 = new MahjongTile(type, num - 1);
            MahjongTile tile2 = new MahjongTile(type, num + 1);
            if (containsTile(hand, tile1) && containsTile(hand, tile2)) {
                List<String> option = Arrays.asList(tile1.toCode(), tile2.toCode());
                options.add(option);
            }
        }

        // 检查吃法3: 吃的牌在右边
        if (num >= 3) {
            MahjongTile tile1 = new MahjongTile(type, num - 2);
            MahjongTile tile2 = new MahjongTile(type, num - 1);
            if (containsTile(hand, tile1) && containsTile(hand, tile2)) {
                List<String> option = Arrays.asList(tile1.toCode(), tile2.toCode());
                options.add(option);
            }
        }

        return options;
    }

    @Override
    public void chi(int seat, MahjongTile discardedTile, int fromSeat, List<MahjongTile> chiTiles) {
        List<MahjongTile> hand = playerHands.get(seat);

        // 从手牌移除用于吃的两张牌
        for (MahjongTile tile : chiTiles) {
            removeTileFromHand(hand, tile);
        }

        // 创建吃牌组合
        List<MahjongTile> meldTiles = new ArrayList<>();
        meldTiles.add(discardedTile.copy());
        for (MahjongTile tile : chiTiles) {
            meldTiles.add(tile.copy());
        }
        // 排序使顺子有序
        Collections.sort(meldTiles);

        Meld meld = new Meld(Meld.MeldType.CHI, meldTiles, fromSeat, false);
        playerMelds.get(seat).add(meld);

        // 从出牌者弃牌中移除
        List<MahjongTile> discards = playerDiscards.get(fromSeat);
        if (!discards.isEmpty()) {
            discards.remove(discards.size() - 1);
        }

        lastActionWasKong = false;
    }

    @Override
    public boolean canPong(int seat, MahjongTile discardedTile) {
        if (discardedTile == null) return false;

        // 打出的百搭牌不能碰(因为已变成普通牌)
        // 正常判断
        return super.canPong(seat, discardedTile);
    }

    @Override
    public boolean canMingKong(int seat, MahjongTile discardedTile) {
        if (discardedTile == null) return false;

        // 打出的牌不能是原来的百搭牌
        return super.canMingKong(seat, discardedTile);
    }

    @Override
    public List<MahjongTile> getAnKongOptions(int seat) {
        List<MahjongTile> options = super.getAnKongOptions(seat);

        // 百搭牌不能暗杠
        return options.stream()
                .filter(t -> !isWildTile(t))
                .collect(Collectors.toList());
    }

    @Override
    public List<MahjongTile> getBuKongOptions(int seat) {
        List<MahjongTile> options = super.getBuKongOptions(seat);

        // 百搭牌不能补杠
        return options.stream()
                .filter(t -> !isWildTile(t))
                .collect(Collectors.toList());
    }
}
