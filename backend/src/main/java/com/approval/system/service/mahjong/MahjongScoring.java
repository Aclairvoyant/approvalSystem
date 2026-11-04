package com.approval.system.service.mahjong;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 计花、计番、算倍数。只看牌面，不改状态。
 */
public final class MahjongScoring {

    private MahjongScoring() {
    }

    /**
     * 按当前牌面评估 seat 能否以 winningTile 胡，能胡返回计分明细（未填付分人），不能胡返回 null。
     * <p>
     * 自摸时 winningTile 已经在手里；接炮时 winningTile 是别人打出的牌，按本身牌面算（不当百搭）。
     * 只判断牌型和计分，「必须先敲」「有百搭只能自摸」「起胡花数」由 {@link #meetsWinRequirement} 判断。
     */
    public static MahjongWin evaluate(MahjongRound round, int seat, int winningTile, boolean selfDraw) {
        MahjongRules rules = MahjongRules.of(round);
        MahjongPlayer player = round.player(seat);
        Integer wild = rules.isWildEnabled() ? round.getWild() : null;

        int[] counts = new int[MahjongTiles.COUNT_SIZE];
        int jokers = 0;
        for (int tile : player.getHand()) {
            if (wild != null && tile == wild) {
                jokers++;
            } else {
                counts[tile]++;
            }
        }
        if (!selfDraw) {
            counts[winningTile]++;
        }

        int meldsNeeded = 4 - player.getMelds().size();
        boolean fourWild = rules.isWildEnabled() && selfDraw && jokers == 4;
        MahjongHandAnalyzer.ShapeInfo shape = null;
        if (!fourWild) {
            shape = MahjongHandAnalyzer.analyze(counts, jokers, meldsNeeded);
            if (shape == null) {
                return null;
            }
        }

        MahjongWin win = new MahjongWin();
        win.setSeat(seat);
        win.setSelfDraw(selfDraw);
        win.setWinningTile(winningTile);
        win.setFourWild(fourWild);

        int handFlowers = countFlowers(rules, player, shape, jokers, win);
        win.setHandFlowers(handFlowers);
        if (rules.getWuHuaGuoFlowers() > 0 && handFlowers == 0 && !fourWild) {
            win.setWuHuaGuo(true);
            win.setFlowers(rules.getWuHuaGuoFlowers());
            win.getFlowerItems().add(new MahjongScoreItem("无花果（含底花）", rules.getWuHuaGuoFlowers()));
        } else {
            win.setFlowers(handFlowers + rules.getBaseFlowers());
            win.getFlowerItems().add(new MahjongScoreItem("底花", rules.getBaseFlowers()));
        }

        int fan = countFan(rules, round, player, shape, counts, jokers, selfDraw, fourWild, win);
        win.setFan(fan);
        win.setMultiple(win.getFlowers() * (1L << Math.min(fan, 40)));
        return win;
    }

    /**
     * 除牌型以外的胡牌条件：敲麻必须先敲并满足起胡花数（无花果视为满足），百搭手里有百搭时只能自摸。
     */
    public static boolean meetsWinRequirement(MahjongRound round, int seat, MahjongWin win) {
        if (win == null) {
            return false;
        }
        MahjongRules rules = MahjongRules.of(round);
        MahjongPlayer player = round.player(seat);
        if (rules.isQiaoRequired() && !player.isQiao()) {
            return false;
        }
        if (rules.isWildEnabled() && !win.isSelfDraw()
                && MahjongTiles.count(player.getHand(), round.getWild()) > 0) {
            return false;
        }
        if (win.isWuHuaGuo() || win.isFourWild()) {
            return true;
        }
        int min = win.isSelfDraw() ? rules.getSelfDrawMinFlowers() : rules.getDiscardWinMinFlowers();
        return win.getHandFlowers() >= min;
    }

    /** 亮在桌上的花数：花牌 + 各副吃碰杠，结算页给没胡的人展示 */
    public static int exposedFlowers(MahjongRound round, MahjongPlayer player) {
        MahjongRules rules = MahjongRules.of(round);
        int total = player.getFlowers().size() * rules.getFlowerTileValue();
        for (MahjongMeld meld : player.getMelds()) {
            total += meldFlowers(rules, meld);
        }
        return total;
    }

    static int meldFlowers(MahjongRules rules, MahjongMeld meld) {
        boolean wind = MahjongTiles.isWind(meld.firstTile());
        return switch (meld.getType()) {
            case CHI -> 0;
            case PONG -> wind ? rules.getWindPongValue() : rules.getSuitedPongValue();
            case MING_GANG, JIA_GANG -> wind ? rules.getWindExposedKongValue() : rules.getSuitedExposedKongValue();
            case AN_GANG -> wind ? rules.getWindConcealedKongValue() : rules.getSuitedConcealedKongValue();
        };
    }

    private static int countFlowers(MahjongRules rules, MahjongPlayer player, MahjongHandAnalyzer.ShapeInfo shape,
                                    int jokers, MahjongWin win) {
        int total = 0;
        int flowerTiles = player.getFlowers().size();
        if (flowerTiles > 0) {
            int value = flowerTiles * rules.getFlowerTileValue();
            win.getFlowerItems().add(new MahjongScoreItem("花牌 ×" + flowerTiles, value));
            total += value;
        }
        for (MahjongMeld meld : player.getMelds()) {
            int value = meldFlowers(rules, meld);
            if (value > 0) {
                win.getFlowerItems().add(new MahjongScoreItem(meldName(meld), value));
                total += value;
            }
        }
        if (shape != null && shape.maxWindTriplets() > 0 && rules.getWindPongValue() > 0) {
            int value = shape.maxWindTriplets() * rules.getWindPongValue();
            win.getFlowerItems().add(new MahjongScoreItem("风暗刻 ×" + shape.maxWindTriplets(), value));
            total += value;
        }
        if (jokers > 0 && rules.getWildInHandValue() > 0) {
            int value = jokers * rules.getWildInHandValue();
            win.getFlowerItems().add(new MahjongScoreItem("百搭 ×" + jokers, value));
            total += value;
        }
        return total;
    }

    private static int countFan(MahjongRules rules, MahjongRound round, MahjongPlayer player,
                                MahjongHandAnalyzer.ShapeInfo shape, int[] counts, int jokers,
                                boolean selfDraw, boolean fourWild, MahjongWin win) {
        int fan = 0;
        if (fourWild) {
            fan += addFan(win, "四百搭", rules.getFanFourWild());
        } else {
            List<MahjongMeld> melds = player.getMelds();
            Set<Integer> suits = new HashSet<>();
            boolean hasWind = false;
            for (int tile = 0; tile < counts.length; tile++) {
                if (counts[tile] > 0) {
                    if (MahjongTiles.isSuited(tile)) {
                        suits.add(MahjongTiles.suit(tile));
                    } else if (MahjongTiles.isWind(tile)) {
                        hasWind = true;
                    }
                }
            }
            boolean hasChi = false;
            boolean open = false;
            for (MahjongMeld meld : melds) {
                int tile = meld.firstTile();
                if (MahjongTiles.isSuited(tile)) {
                    suits.add(MahjongTiles.suit(tile));
                } else if (MahjongTiles.isWind(tile)) {
                    hasWind = true;
                }
                hasChi |= meld.getType() == MahjongMeld.Type.CHI;
                open |= meld.isOpen();
            }
            // 百搭可以变成任意牌，带百搭时不算清一色、混一色（只有敲麻算这两个番，敲麻没有百搭）
            if (jokers == 0) {
                if (suits.size() == 1 && !hasWind) {
                    fan += addFan(win, "清一色", rules.getFanQingYiSe());
                } else if (suits.size() <= 1 && hasWind) {
                    fan += addFan(win, "混一色", rules.getFanHunYiSe());
                }
            }
            if (!hasChi && shape != null && shape.allTripletsPossible()) {
                fan += addFan(win, "碰碰胡", rules.getFanPengPengHu());
            }
            if (!open) {
                fan += addFan(win, "门清", rules.getFanMenQing());
            }
            if (selfDraw && round.isReplacementDraw()) {
                fan += addFan(win, "杠开", rules.getFanGangKai());
            }
            if (melds.size() == 4) {
                fan += addFan(win, "大吊车", rules.getFanDaDiao());
            }
            if (rules.isWildEnabled() && jokers == 0) {
                fan += addFan(win, "无百搭", rules.getFanNoWild());
            }
        }
        if (round.getHuangCount() > 0 && rules.getFanPerHuang() > 0) {
            int value = round.getHuangCount() * rules.getFanPerHuang();
            win.getFanItems().add(new MahjongScoreItem("荒番 ×" + round.getHuangCount(), value));
            fan += value;
        }
        return fan;
    }

    private static int addFan(MahjongWin win, String name, int value) {
        if (value <= 0) {
            return 0;
        }
        win.getFanItems().add(new MahjongScoreItem(name, value));
        return value;
    }

    private static String meldName(MahjongMeld meld) {
        String tile = MahjongTiles.isWind(meld.firstTile())
                ? MahjongTiles.name(meld.firstTile()) + "风"
                : MahjongTiles.name(meld.firstTile());
        return switch (meld.getType()) {
            case CHI -> tile + "吃";
            case PONG -> tile + "碰";
            case MING_GANG -> tile + "明杠";
            case JIA_GANG -> tile + "加杠";
            case AN_GANG -> tile + "暗杠";
        };
    }
}
