package com.approval.system.service.mahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌的编码与基本属性。
 * <p>
 * 编码 = 花色 × 10 + 点数：万 11–19，条 21–29，筒 31–39，东南西北 41–44，中发白 45–47，
 * 春夏秋冬梅兰竹菊 51–58。同一花色相邻的数牌编码也相邻，跨花色之间隔着 x0（不存在的牌），
 * 所以顺子判断只需看编码是否连续。
 * <p>
 * 上海麻将（百搭、敲麻）里中发白和八张花牌一样算花：摸到就亮出来补牌，不能留在手里。
 */
public final class MahjongTiles {

    public static final int SUIT_WAN = 1;
    public static final int SUIT_TIAO = 2;
    public static final int SUIT_TONG = 3;

    public static final int EAST = 41;
    public static final int SOUTH = 42;
    public static final int WEST = 43;
    public static final int NORTH = 44;
    public static final int RED = 45;
    public static final int GREEN = 46;
    public static final int WHITE = 47;
    public static final int FLOWER_FIRST = 51;
    public static final int FLOWER_LAST = 58;

    /** 计数数组的长度：下标就是牌的编码 */
    public static final int COUNT_SIZE = 60;

    public static final int TOTAL_TILES = 144;

    private static final String[] RANK_NAMES = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] SUIT_NAMES = {"", "万", "条", "筒"};
    private static final String[] HONOR_NAMES = {"东", "南", "西", "北", "中", "发", "白"};
    private static final String[] FLOWER_NAMES = {"春", "夏", "秋", "冬", "梅", "兰", "竹", "菊"};

    /** 手里能留着的牌：27 张数牌 + 4 种风牌 */
    private static final List<Integer> PLAYABLE_KINDS;

    static {
        List<Integer> kinds = new ArrayList<>();
        for (int suit = SUIT_WAN; suit <= SUIT_TONG; suit++) {
            for (int rank = 1; rank <= 9; rank++) {
                kinds.add(suit * 10 + rank);
            }
        }
        for (int wind = EAST; wind <= NORTH; wind++) {
            kinds.add(wind);
        }
        PLAYABLE_KINDS = Collections.unmodifiableList(kinds);
    }

    private MahjongTiles() {
    }

    public static boolean isValid(int tile) {
        return isSuited(tile) || (tile >= EAST && tile <= WHITE) || isFlowerCard(tile);
    }

    public static boolean isSuited(int tile) {
        int suit = tile / 10;
        int rank = tile % 10;
        return suit >= SUIT_WAN && suit <= SUIT_TONG && rank >= 1 && rank <= 9;
    }

    public static int suit(int tile) {
        return tile / 10;
    }

    public static int rank(int tile) {
        return tile % 10;
    }

    public static boolean isWind(int tile) {
        return tile >= EAST && tile <= NORTH;
    }

    public static boolean isDragon(int tile) {
        return tile >= RED && tile <= WHITE;
    }

    public static boolean isFlowerCard(int tile) {
        return tile >= FLOWER_FIRST && tile <= FLOWER_LAST;
    }

    /** 中发白和春夏秋冬梅兰竹菊都算花 */
    public static boolean isFlower(int tile) {
        return isDragon(tile) || isFlowerCard(tile);
    }

    public static List<Integer> playableKinds() {
        return PLAYABLE_KINDS;
    }

    /** 一副完整的 144 张牌（未洗） */
    public static List<Integer> fullSet() {
        List<Integer> tiles = new ArrayList<>(TOTAL_TILES);
        for (int suit = SUIT_WAN; suit <= SUIT_TONG; suit++) {
            for (int rank = 1; rank <= 9; rank++) {
                addCopies(tiles, suit * 10 + rank, 4);
            }
        }
        for (int honor = EAST; honor <= WHITE; honor++) {
            addCopies(tiles, honor, 4);
        }
        for (int flower = FLOWER_FIRST; flower <= FLOWER_LAST; flower++) {
            tiles.add(flower);
        }
        return tiles;
    }

    /**
     * 百搭：引导牌的下一张。数牌 9→1，风牌 东→南→西→北→东；翻到花（含中发白）时东风为百搭。
     */
    public static int wildAfter(int indicator) {
        if (isSuited(indicator)) {
            return rank(indicator) == 9 ? suit(indicator) * 10 + 1 : indicator + 1;
        }
        if (isWind(indicator)) {
            return indicator == NORTH ? EAST : indicator + 1;
        }
        return EAST;
    }

    public static String name(int tile) {
        if (isSuited(tile)) {
            return RANK_NAMES[rank(tile)] + SUIT_NAMES[suit(tile)];
        }
        if (tile >= EAST && tile <= WHITE) {
            return HONOR_NAMES[tile - EAST];
        }
        if (isFlowerCard(tile)) {
            return FLOWER_NAMES[tile - FLOWER_FIRST];
        }
        return "?";
    }

    public static int[] counts(Iterable<Integer> tiles) {
        int[] counts = new int[COUNT_SIZE];
        for (Integer tile : tiles) {
            counts[tile]++;
        }
        return counts;
    }

    public static int count(List<Integer> tiles, int tile) {
        int count = 0;
        for (Integer item : tiles) {
            if (item == tile) {
                count++;
            }
        }
        return count;
    }

    private static void addCopies(List<Integer> tiles, int tile, int copies) {
        for (int i = 0; i < copies; i++) {
            tiles.add(tile);
        }
    }
}
