package com.approval.system.service.mahjong;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引擎产生的事件，服务层写进动作日志，并把公开部分推给房间。
 * hidden=true 的事件（摸牌）牌面只有座位本人能看，推给房间时去掉 tile。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MahjongEvent {

    public static final String DRAW = "DRAW";
    public static final String FLOWER = "FLOWER";
    public static final String DISCARD = "DISCARD";
    public static final String QIAO = "QIAO";
    public static final String CHI = "CHI";
    public static final String PONG = "PONG";
    public static final String MING_GANG = "MING_GANG";
    public static final String JIA_GANG = "JIA_GANG";
    public static final String AN_GANG = "AN_GANG";
    public static final String HU = "HU";
    public static final String PASS = "PASS";
    public static final String DRAW_GAME = "DRAW_GAME";
    public static final String AUTO_ON = "AUTO_ON";
    public static final String AUTO_OFF = "AUTO_OFF";

    private String type;
    private int seat;
    private Integer tile;
    private boolean hidden;

    public static MahjongEvent open(String type, int seat, Integer tile) {
        return new MahjongEvent(type, seat, tile, false);
    }

    public static MahjongEvent secret(String type, int seat, Integer tile) {
        return new MahjongEvent(type, seat, tile, true);
    }
}
