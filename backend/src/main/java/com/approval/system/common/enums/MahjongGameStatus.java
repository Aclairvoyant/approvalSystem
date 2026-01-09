package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将游戏状态枚举
 */
@Getter
public enum MahjongGameStatus {
    WAITING(1, "等待加入"),
    PLAYING(2, "游戏中"),
    FINISHED(3, "已结束"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String name;

    MahjongGameStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static MahjongGameStatus fromCode(int code) {
        for (MahjongGameStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return WAITING;
    }
}
