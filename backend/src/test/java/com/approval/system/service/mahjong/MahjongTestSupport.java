package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试里用简写描述牌：m 万、s 条、p 筒、z 字（1–4 东南西北，5–7 中发白）、f 花（1–8 春夏秋冬梅兰竹菊）。
 * 例如 "123m 456s 11z" = 一二三万、四五六条、两张东风。
 */
final class MahjongTestSupport {

    private MahjongTestSupport() {
    }

    static List<Integer> tiles(String notation) {
        List<Integer> tiles = new ArrayList<>();
        List<Integer> pending = new ArrayList<>();
        for (char ch : notation.replace(" ", "").toCharArray()) {
            if (Character.isDigit(ch)) {
                pending.add(ch - '0');
                continue;
            }
            int base = switch (ch) {
                case 'm' -> 10;
                case 's' -> 20;
                case 'p' -> 30;
                case 'z' -> 40;
                case 'f' -> 50;
                default -> throw new IllegalArgumentException("未知花色: " + ch);
            };
            for (int digit : pending) {
                tiles.add(base + digit);
            }
            pending.clear();
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("缺少花色: " + notation);
        }
        return tiles;
    }

    static int tile(String notation) {
        List<Integer> parsed = tiles(notation);
        if (parsed.size() != 1) {
            throw new IllegalArgumentException(notation);
        }
        return parsed.get(0);
    }

    static int[] counts(String notation) {
        return MahjongTiles.counts(tiles(notation));
    }

    /**
     * 手工摆一局：所有人手牌为空，牌墙用 wall 描述（下标 0 先摸，末尾补花补杠），轮到 turn 出牌。
     */
    static MahjongRound round(MahjongModeEnum mode, int seatCount, String wall) {
        MahjongRound round = new MahjongRound();
        round.setMode(mode);
        round.setSeatCount(seatCount);
        round.setRoundNo(1);
        round.setDealer(0);
        round.setCapPoints(MahjongRules.DEFAULT_CAP_POINTS);
        round.setWall(new ArrayList<>(tiles(wall)));
        for (int seat = 0; seat < seatCount; seat++) {
            round.getPlayers().add(new MahjongPlayer(seat, false));
        }
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(0);
        return round;
    }

    static void hand(MahjongRound round, int seat, String notation) {
        round.player(seat).setHand(new ArrayList<>(tiles(notation)));
    }

    /** seat 正在出牌，手里是 hand + 刚摸到的 drawn */
    static void turnWithDraw(MahjongRound round, int seat, String hand, String drawn) {
        hand(round, seat, hand);
        int tile = tile(drawn);
        round.player(seat).getHand().add(tile);
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(seat);
        round.setDrawnTile(tile);
    }

    static void flowers(MahjongRound round, int seat, String notation) {
        round.player(seat).setFlowers(new ArrayList<>(tiles(notation)));
    }

    static void meld(MahjongRound round, int seat, MahjongMeld.Type type, String notation, Integer fromSeat) {
        List<Integer> tiles = tiles(notation);
        round.player(seat).getMelds().add(new MahjongMeld(type, new ArrayList<>(tiles), fromSeat,
                fromSeat == null ? null : tiles.get(0)));
    }

    /** 当前牌局里所有牌的张数（牌墙 + 手牌 + 副露 + 花 + 牌河 + 百搭引导牌 + 接炮的那张） */
    static int totalTiles(MahjongRound round) {
        int total = round.getWall().size() + (round.getIndicator() != null ? 1 : 0);
        for (MahjongPlayer player : round.getPlayers()) {
            total += player.getHand().size() + player.getFlowers().size() + player.getDiscards().size();
            for (MahjongMeld meld : player.getMelds()) {
                total += meld.getTiles().size();
            }
        }
        MahjongRoundResult result = round.getResult();
        if (result != null && !result.isDrawGame() && !result.isSelfDraw()) {
            total += 1;
        }
        return total;
    }
}
