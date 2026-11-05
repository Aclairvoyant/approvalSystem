package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 纪念日重复方式（couple_events.repeat_type）
 */
@Getter
@AllArgsConstructor
public enum RepeatTypeEnum {
    NONE(0, "不重复"),
    YEARLY(1, "每年");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static RepeatTypeEnum getByCode(Integer code) {
        for (RepeatTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
