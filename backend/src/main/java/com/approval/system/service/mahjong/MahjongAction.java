package com.approval.system.service.mahjong;

/**
 * 一次操作。tile：出牌 / 敲 / 自己回合杠的牌；chiStart：吃的顺子里最小的那张。
 */
public record MahjongAction(MahjongActionType type, Integer tile, Integer chiStart) {

    public static MahjongAction of(MahjongActionType type) {
        return new MahjongAction(type, null, null);
    }

    public static MahjongAction discard(int tile) {
        return new MahjongAction(MahjongActionType.DISCARD, tile, null);
    }

    public static MahjongAction qiao(int tile) {
        return new MahjongAction(MahjongActionType.QIAO, tile, null);
    }

    public static MahjongAction kong(int tile) {
        return new MahjongAction(MahjongActionType.KONG, tile, null);
    }

    public static MahjongAction chi(int start) {
        return new MahjongAction(MahjongActionType.CHI, null, start);
    }
}
