package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GameStatusEnum {
    WAITING(1, "等待加入"),
    PLAYING(2, "游戏中"),
    FINISHED(3, "已结束"),
    CANCELLED(4, "已取消");

    private final Integer code;
    private final String desc;

    public static GameStatusEnum getByCode(Integer code) {
        for (GameStatusEnum status : GameStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
