package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GameMoveTypeEnum {
    ROLL_DICE(1, "掷骰子"),
    MOVE_PIECE(2, "移动棋子"),
    CAPTURE(3, "吃子"),
    TRIGGER_TASK(4, "触发任务"),
    COMPLETE_TASK(5, "完成任务");

    private final Integer code;
    private final String desc;

    public static GameMoveTypeEnum getByCode(Integer code) {
        for (GameMoveTypeEnum type : GameMoveTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
