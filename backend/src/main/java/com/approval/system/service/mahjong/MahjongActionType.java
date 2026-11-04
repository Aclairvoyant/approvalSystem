package com.approval.system.service.mahjong;

/**
 * 玩家可以发出的操作。
 */
public enum MahjongActionType {
    /** 出牌 */
    DISCARD,
    /** 敲麻：打出一张并声明听牌 */
    QIAO,
    /** 胡（自摸或接炮） */
    HU,
    /** 杠：自己回合是暗杠 / 加杠，响应别人出牌时是明杠 */
    KONG,
    PONG,
    CHI,
    /** 不要别人打出的牌 */
    PASS,
    /** 取消托管 */
    CANCEL_AUTO
}
