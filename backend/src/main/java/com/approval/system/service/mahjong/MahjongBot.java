package com.approval.system.service.mahjong;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 电脑玩家（也用于托管和超时代打）的简单策略：
 * <ul>
 *   <li>能胡就胡；能敲就敲（选听牌最多的打法）；</li>
 *   <li>出牌选打完向听数最小的那张，平手时打孤张、边张、幺九和风牌；百搭留着不打；</li>
 *   <li>碰、吃只在能减少向听数时才要（风碰算花，不变差也碰）；杠不用百搭。</li>
 * </ul>
 */
public final class MahjongBot {

    private MahjongBot() {
    }

    public static MahjongAction decideTurn(MahjongRound round, int seat) {
        MahjongSeatOptions options = MahjongEngine.options(round, seat);
        if (options.isCanHu()) {
            return MahjongAction.of(MahjongActionType.HU);
        }
        MahjongPlayer player = round.player(seat);
        Integer drawn = round.getDrawnTile();
        if (player.isQiao()) {
            if (drawn != null && options.getKongTiles().contains(drawn)) {
                return MahjongAction.kong(drawn);
            }
            List<Integer> hand = player.getHand();
            return MahjongAction.discard(drawn != null ? drawn : hand.get(hand.size() - 1));
        }
        for (int tile : options.getKongTiles()) {
            if (!isWild(round, tile)) {
                return MahjongAction.kong(tile);
            }
        }
        if (!options.getQiaoOptions().isEmpty()) {
            MahjongSeatOptions.QiaoOption best = options.getQiaoOptions().get(0);
            for (MahjongSeatOptions.QiaoOption option : options.getQiaoOptions()) {
                if (option.waits().size() > best.waits().size()) {
                    best = option;
                }
            }
            return MahjongAction.qiao(best.tile());
        }
        return MahjongAction.discard(chooseDiscard(round, player, options.getDiscardable()));
    }

    public static MahjongAction decideClaim(MahjongRound round, MahjongClaim claim) {
        if (claim.isHu()) {
            return MahjongAction.of(MahjongActionType.HU);
        }
        MahjongPlayer player = round.player(claim.getSeat());
        int tile = round.getLastDiscard();
        if (player.isQiao() || isWild(round, tile)) {
            return MahjongAction.of(MahjongActionType.PASS);
        }
        int meldsNeeded = 4 - player.getMelds().size();
        int base = shanten(round, player.getHand(), meldsNeeded);

        if (claim.isKong()) {
            List<Integer> rest = without(player.getHand(), tile, tile, tile);
            if (shanten(round, rest, meldsNeeded - 1) <= base) {
                return MahjongAction.of(MahjongActionType.KONG);
            }
        }
        if (claim.isPong()) {
            List<Integer> rest = without(player.getHand(), tile, tile);
            int after = bestAfterDiscard(round, rest, meldsNeeded - 1, tile);
            if (after < base || (MahjongTiles.isWind(tile) && after <= base)) {
                return MahjongAction.of(MahjongActionType.PONG);
            }
        }
        for (int start : claim.getChiStarts()) {
            List<Integer> used = new ArrayList<>();
            for (int t = start; t < start + 3; t++) {
                if (t != tile) {
                    used.add(t);
                }
            }
            if (used.stream().anyMatch(t -> isWild(round, t))) {
                continue;
            }
            List<Integer> rest = without(player.getHand(), used.get(0), used.get(1));
            if (bestAfterDiscard(round, rest, meldsNeeded - 1, tile) < base) {
                return MahjongAction.chi(start);
            }
        }
        return MahjongAction.of(MahjongActionType.PASS);
    }

    static int chooseDiscard(MahjongRound round, MahjongPlayer player, List<Integer> candidates) {
        List<Integer> pool = candidates.stream().filter(tile -> !isWild(round, tile)).toList();
        if (pool.isEmpty()) {
            pool = candidates;
        }
        int meldsNeeded = 4 - player.getMelds().size();
        int[] fullCounts = realCounts(round, player.getHand());
        int bestTile = pool.get(0);
        int bestShanten = Integer.MAX_VALUE;
        int bestKeep = Integer.MAX_VALUE;
        for (int tile : pool) {
            int after = shanten(round, without(player.getHand(), tile), meldsNeeded);
            int keep = keepValue(fullCounts, tile);
            if (after < bestShanten || (after == bestShanten && keep < bestKeep)) {
                bestTile = tile;
                bestShanten = after;
                bestKeep = keep;
            }
        }
        return bestTile;
    }

    private static int bestAfterDiscard(MahjongRound round, List<Integer> hand, int meldsNeeded, int forbidden) {
        int best = Integer.MAX_VALUE;
        for (int tile : new TreeSet<>(hand)) {
            if (tile == forbidden || isWild(round, tile)) {
                continue;
            }
            best = Math.min(best, shanten(round, without(hand, tile), meldsNeeded));
        }
        return best == Integer.MAX_VALUE ? 8 : best;
    }

    /** 越大越值得留 */
    private static int keepValue(int[] counts, int tile) {
        int value = 0;
        if (counts[tile] >= 2) {
            value += 6 * (counts[tile] - 1);
        }
        if (MahjongTiles.isSuited(tile)) {
            int rank = MahjongTiles.rank(tile);
            if (rank >= 2 && counts[tile - 1] > 0) {
                value += 3;
            }
            if (rank <= 8 && counts[tile + 1] > 0) {
                value += 3;
            }
            if (rank >= 3 && counts[tile - 2] > 0) {
                value += 1;
            }
            if (rank <= 7 && counts[tile + 2] > 0) {
                value += 1;
            }
            value += (rank == 1 || rank == 9) ? 0 : (rank == 2 || rank == 8) ? 1 : 2;
        }
        return value;
    }

    private static int shanten(MahjongRound round, List<Integer> hand, int meldsNeeded) {
        Integer wild = wildTile(round);
        int[] counts = new int[MahjongTiles.COUNT_SIZE];
        int jokers = 0;
        for (int tile : hand) {
            if (wild != null && tile == wild) {
                jokers++;
            } else {
                counts[tile]++;
            }
        }
        return MahjongHandAnalyzer.shanten(counts, jokers, meldsNeeded);
    }

    private static int[] realCounts(MahjongRound round, List<Integer> hand) {
        Integer wild = wildTile(round);
        int[] counts = new int[MahjongTiles.COUNT_SIZE];
        for (int tile : hand) {
            if (wild == null || tile != wild) {
                counts[tile]++;
            }
        }
        return counts;
    }

    private static boolean isWild(MahjongRound round, int tile) {
        Integer wild = wildTile(round);
        return wild != null && wild == tile;
    }

    private static Integer wildTile(MahjongRound round) {
        return MahjongRules.of(round).isWildEnabled() ? round.getWild() : null;
    }

    private static List<Integer> without(List<Integer> hand, int... tiles) {
        List<Integer> rest = new ArrayList<>(hand);
        for (int tile : tiles) {
            rest.remove(Integer.valueOf(tile));
        }
        return rest;
    }
}
