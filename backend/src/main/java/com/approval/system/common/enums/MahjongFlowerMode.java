package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 花牌模式枚举（百搭模式专用）
 */
@Getter
public enum MahjongFlowerMode {
    FLOWER_8(8, "8花", "标准8张花牌：春夏秋冬梅兰竹菊"),
    FLOWER_20(20, "20花", "8原花 + 东南西北各3张当花（每种留1张配对）"),
    FLOWER_36(36, "36花", "8原花 + 风牌16张 + 箭牌12张全当花");

    private final int code;
    private final String name;
    private final String description;

    MahjongFlowerMode(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public static MahjongFlowerMode fromCode(int code) {
        for (MahjongFlowerMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return FLOWER_8;
    }
}
