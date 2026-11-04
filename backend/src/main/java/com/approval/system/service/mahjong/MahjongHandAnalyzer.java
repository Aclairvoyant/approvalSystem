package com.approval.system.service.mahjong;

import java.util.ArrayList;
import java.util.List;

/**
 * 胡牌判定（4 副面子 + 1 对将，可带百搭替代）、听牌和向听数。
 * <p>
 * counts 是按牌编码计数的数组（见 {@link MahjongTiles}），只放真实牌；百搭单独用 jokers 传入，
 * 每张百搭可以当作任意一张非花牌。meldsNeeded = 4 - 已亮出的副数。
 * <p>
 * 搜索总是先处理编码最小的那张真实牌：它要么做将，要么组成刻子，要么做顺子里最小的真实牌
 * （比它小的位置只能由百搭补），所以只需要尝试有限几种组合。
 */
public final class MahjongHandAnalyzer {

    private MahjongHandAnalyzer() {
    }

    /** 拆牌统计：是否存在全是刻子的拆法、风刻最多几副（含百搭凑成的） */
    public record ShapeInfo(boolean allTripletsPossible, int maxWindTriplets) {
    }

    public static boolean isWinningShape(int[] counts, int jokers, int meldsNeeded) {
        if (!sizeMatches(counts, jokers, meldsNeeded)) {
            return false;
        }
        return canComplete(counts.clone(), jokers, meldsNeeded, true, 0);
    }

    /**
     * 能胡时返回拆牌统计，不能胡返回 null。两项分别取所有拆法里对胡牌者最有利的值。
     */
    public static ShapeInfo analyze(int[] counts, int jokers, int meldsNeeded) {
        if (!sizeMatches(counts, jokers, meldsNeeded)) {
            return null;
        }
        Search search = new Search();
        search.explore(counts.clone(), jokers, meldsNeeded, true, 0, 0, true);
        if (!search.found) {
            return null;
        }
        return new ShapeInfo(search.allTriplets, search.maxWindTriplets);
    }

    /** 听哪些牌：手里 3n+1 张时，再来哪一种牌就能胡 */
    public static List<Integer> waits(int[] counts, int jokers, int meldsNeeded) {
        List<Integer> waits = new ArrayList<>();
        int[] work = counts.clone();
        for (int tile : MahjongTiles.playableKinds()) {
            work[tile]++;
            if (isWinningShape(work, jokers, meldsNeeded)) {
                waits.add(tile);
            }
            work[tile]--;
        }
        return waits;
    }

    /**
     * 向听数：-1 已经胡了，0 听牌，1 差一张听牌……只给电脑估算用；百搭按每张减一向听粗算。
     */
    public static int shanten(int[] counts, int jokers, int meldsNeeded) {
        int exposed = 4 - meldsNeeded;
        int[] work = counts.clone();
        int best = 8 - 2 * exposed - maxBlocks(work, 0, meldsNeeded);
        for (int tile = 0; tile < MahjongTiles.COUNT_SIZE; tile++) {
            if (work[tile] >= 2) {
                work[tile] -= 2;
                best = Math.min(best, 8 - 2 * exposed - 1 - maxBlocks(work, 0, meldsNeeded));
                work[tile] += 2;
            }
        }
        return Math.max(-1, best - jokers);
    }

    private static boolean sizeMatches(int[] counts, int jokers, int meldsNeeded) {
        if (meldsNeeded < 0 || jokers < 0) {
            return false;
        }
        int total = jokers;
        for (int count : counts) {
            total += count;
        }
        return total == meldsNeeded * 3 + 2;
    }

    private static boolean canComplete(int[] c, int jokers, int melds, boolean needPair, int from) {
        int i = firstTile(c, from);
        if (i < 0) {
            // 真实牌用完了，剩下的百搭自己组成面子和将（张数已经校验过，一定刚好）
            return jokers == melds * 3 + (needPair ? 2 : 0);
        }
        if (needPair) {
            if (c[i] >= 2 && tryUse(c, jokers, melds, false, i, i, i, -1)) {
                return true;
            }
            if (jokers >= 1 && tryUse(c, jokers - 1, melds, false, i, i, -1, -1)) {
                return true;
            }
        }
        if (melds == 0) {
            return false;
        }
        if (c[i] >= 3 && tryUse(c, jokers, melds - 1, needPair, i, i, i, i)) {
            return true;
        }
        if (c[i] >= 2 && jokers >= 1 && tryUse(c, jokers - 1, melds - 1, needPair, i, i, i, -1)) {
            return true;
        }
        if (jokers >= 2 && tryUse(c, jokers - 2, melds - 1, needPair, i, i, -1, -1)) {
            return true;
        }
        if (MahjongTiles.isSuited(i)) {
            int rank = MahjongTiles.rank(i);
            if (rank <= 7 && c[i + 1] > 0 && c[i + 2] > 0
                    && tryUse(c, jokers, melds - 1, needPair, i, i, i + 1, i + 2)) {
                return true;
            }
            if (rank <= 8 && c[i + 1] > 0 && jokers >= 1
                    && tryUse(c, jokers - 1, melds - 1, needPair, i, i, i + 1, -1)) {
                return true;
            }
            if (rank <= 7 && c[i + 2] > 0 && jokers >= 1
                    && tryUse(c, jokers - 1, melds - 1, needPair, i, i, i + 2, -1)) {
                return true;
            }
        }
        return false;
    }

    /** 从计数里拿掉 a、b、c（-1 表示不拿），递归，再放回去 */
    private static boolean tryUse(int[] counts, int jokers, int melds, boolean needPair, int from,
                                  int a, int b, int d) {
        take(counts, a, b, d);
        boolean ok = canComplete(counts, jokers, melds, needPair, from);
        restore(counts, a, b, d);
        return ok;
    }

    private static void take(int[] counts, int a, int b, int d) {
        if (a >= 0) {
            counts[a]--;
        }
        if (b >= 0) {
            counts[b]--;
        }
        if (d >= 0) {
            counts[d]--;
        }
    }

    private static void restore(int[] counts, int a, int b, int d) {
        if (a >= 0) {
            counts[a]++;
        }
        if (b >= 0) {
            counts[b]++;
        }
        if (d >= 0) {
            counts[d]++;
        }
    }

    private static int firstTile(int[] counts, int from) {
        for (int i = from; i < counts.length; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    /** 枚举所有拆法，记录最有利的统计 */
    private static final class Search {
        boolean found;
        boolean allTriplets;
        int maxWindTriplets = -1;

        void explore(int[] c, int jokers, int melds, boolean needPair, int from,
                     int windTriplets, boolean onlyTriplets) {
            int i = firstTile(c, from);
            if (i < 0) {
                if (jokers != melds * 3 + (needPair ? 2 : 0)) {
                    return;
                }
                found = true;
                // 剩下的百搭三张一组时，按风刻算最有利
                int total = windTriplets + melds;
                maxWindTriplets = Math.max(maxWindTriplets, total);
                if (onlyTriplets) {
                    allTriplets = true;
                }
                return;
            }
            if (needPair) {
                if (c[i] >= 2) {
                    take(c, i, i, -1);
                    explore(c, jokers, melds, false, i, windTriplets, onlyTriplets);
                    restore(c, i, i, -1);
                }
                if (jokers >= 1) {
                    take(c, i, -1, -1);
                    explore(c, jokers - 1, melds, false, i, windTriplets, onlyTriplets);
                    restore(c, i, -1, -1);
                }
            }
            if (melds == 0) {
                return;
            }
            int windBonus = MahjongTiles.isWind(i) ? 1 : 0;
            if (c[i] >= 3) {
                take(c, i, i, i);
                explore(c, jokers, melds - 1, needPair, i, windTriplets + windBonus, onlyTriplets);
                restore(c, i, i, i);
            }
            if (c[i] >= 2 && jokers >= 1) {
                take(c, i, i, -1);
                explore(c, jokers - 1, melds - 1, needPair, i, windTriplets + windBonus, onlyTriplets);
                restore(c, i, i, -1);
            }
            if (jokers >= 2) {
                take(c, i, -1, -1);
                explore(c, jokers - 2, melds - 1, needPair, i, windTriplets + windBonus, onlyTriplets);
                restore(c, i, -1, -1);
            }
            if (MahjongTiles.isSuited(i)) {
                int rank = MahjongTiles.rank(i);
                if (rank <= 7 && c[i + 1] > 0 && c[i + 2] > 0) {
                    take(c, i, i + 1, i + 2);
                    explore(c, jokers, melds - 1, needPair, i, windTriplets, false);
                    restore(c, i, i + 1, i + 2);
                }
                if (rank <= 8 && c[i + 1] > 0 && jokers >= 1) {
                    take(c, i, i + 1, -1);
                    explore(c, jokers - 1, melds - 1, needPair, i, windTriplets, false);
                    restore(c, i, i + 1, -1);
                }
                if (rank <= 7 && c[i + 2] > 0 && jokers >= 1) {
                    take(c, i, i + 2, -1);
                    explore(c, jokers - 1, melds - 1, needPair, i, windTriplets, false);
                    restore(c, i, i + 2, -1);
                }
            }
        }
    }

    /**
     * 向听估算：在不超过 meldsNeeded 个块的前提下，最大化 2×面子 + 搭子。
     */
    private static int maxBlocks(int[] c, int from, int slots) {
        int i = firstTile(c, from);
        if (i < 0 || slots == 0) {
            return 0;
        }
        int best = 0;
        // 面子
        if (c[i] >= 3) {
            c[i] -= 3;
            best = Math.max(best, 2 + maxBlocks(c, i, slots - 1));
            c[i] += 3;
        }
        boolean suited = MahjongTiles.isSuited(i);
        int rank = MahjongTiles.rank(i);
        if (suited && rank <= 7 && c[i + 1] > 0 && c[i + 2] > 0) {
            take(c, i, i + 1, i + 2);
            best = Math.max(best, 2 + maxBlocks(c, i, slots - 1));
            restore(c, i, i + 1, i + 2);
        }
        // 搭子
        if (c[i] >= 2) {
            c[i] -= 2;
            best = Math.max(best, 1 + maxBlocks(c, i, slots - 1));
            c[i] += 2;
        }
        if (suited && rank <= 8 && c[i + 1] > 0) {
            take(c, i, i + 1, -1);
            best = Math.max(best, 1 + maxBlocks(c, i, slots - 1));
            restore(c, i, i + 1, -1);
        }
        if (suited && rank <= 7 && c[i + 2] > 0) {
            take(c, i, i + 2, -1);
            best = Math.max(best, 1 + maxBlocks(c, i, slots - 1));
            restore(c, i, i + 2, -1);
        }
        // 这张当孤张
        c[i]--;
        best = Math.max(best, maxBlocks(c, i, slots));
        c[i]++;
        return best;
    }
}
