package com.approval.system.service.mahjong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 亮在桌上的一副牌（含暗杠）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MahjongMeld {

    public enum Type {
        CHI,
        PONG,
        /** 明杠：别人打出的牌 + 手里三张 */
        MING_GANG,
        /** 加杠：已碰的刻子摸到第四张 */
        JIA_GANG,
        AN_GANG
    }

    private Type type;
    private List<Integer> tiles = new ArrayList<>();
    /** 吃碰明杠的牌是谁打出的；暗杠为 null */
    private Integer fromSeat;
    /** 吃碰明杠时拿进来的那张 */
    private Integer claimedTile;

    @JsonIgnore
    public int firstTile() {
        return tiles.get(0);
    }

    @JsonIgnore
    public boolean isKong() {
        return type == Type.MING_GANG || type == Type.JIA_GANG || type == Type.AN_GANG;
    }

    /** 吃、碰、明杠、加杠都破门清；暗杠不破 */
    @JsonIgnore
    public boolean isOpen() {
        return type != Type.AN_GANG;
    }
}
