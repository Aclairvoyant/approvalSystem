package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将牌类型枚举
 */
@Getter
public enum MahjongTileType {
    // 万子
    WAN(1, "万", "WAN"),
    // 筒子
    TONG(2, "筒", "TONG"),
    // 条子
    TIAO(3, "条", "TIAO"),
    // 风牌
    FENG(4, "风", "FENG"),
    // 箭牌（中发白）
    JIAN(5, "箭", "JIAN"),
    // 花牌
    HUA(6, "花", "HUA");

    private final int code;
    private final String name;
    private final String prefix;

    MahjongTileType(int code, String name, String prefix) {
        this.code = code;
        this.name = name;
        this.prefix = prefix;
    }

    /**
     * 判断是否是数牌（万筒条）
     */
    public boolean isNumberTile() {
        return this == WAN || this == TONG || this == TIAO;
    }

    /**
     * 判断是否是字牌（风牌+箭牌）
     */
    public boolean isHonorTile() {
        return this == FENG || this == JIAN;
    }

    /**
     * 判断是否是花牌
     */
    public boolean isFlowerTile() {
        return this == HUA;
    }
}
