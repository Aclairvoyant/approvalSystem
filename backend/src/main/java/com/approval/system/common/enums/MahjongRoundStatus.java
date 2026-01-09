package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将单局状态枚举
 */
@Getter
public enum MahjongRoundStatus {
    PLAYING(1, "进行中"),
    DRAW(2, "流局(荒番)"),
    HU(3, "胡牌结束");

    private final int code;
    private final String name;

    MahjongRoundStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MahjongRoundStatus fromCode(int code) {
        for (MahjongRoundStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return PLAYING;
    }
}
