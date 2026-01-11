package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将操作类型枚举
 */
@Getter
public enum MahjongActionType {
    // 基础操作
    DRAW("DRAW", "摸牌"),
    DISCARD("DISCARD", "出牌"),

    // 吃碰杠操作
    CHI("CHI", "吃"),
    PONG("PONG", "碰"),
    MING_KONG("MING_KONG", "明杠"),
    AN_KONG("AN_KONG", "暗杠"),
    BU_KONG("BU_KONG", "补杠"),

    // 花牌操作
    BU_HUA("BU_HUA", "补花"),

    // 胡牌操作
    HU("HU", "胡"),

    // 其他
    PASS("PASS", "过"),
    READY("READY", "听牌");

    private final String code;
    private final String name;

    MahjongActionType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MahjongActionType fromCode(String code) {
        for (MahjongActionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
