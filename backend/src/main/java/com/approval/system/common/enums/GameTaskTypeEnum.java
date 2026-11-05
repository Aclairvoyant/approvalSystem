package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游戏任务类型（game_tasks.task_type）
 */
@Getter
@AllArgsConstructor
public enum GameTaskTypeEnum {
    PRESET(1, "预设任务"),
    CUSTOM(2, "自定义任务");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static GameTaskTypeEnum getByCode(Integer code) {
        for (GameTaskTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
