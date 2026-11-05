package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日常事项状态（daily_items.status）
 */
@Getter
@AllArgsConstructor
public enum DailyItemStatusEnum {
    PENDING(1, "待完成"),
    COMPLETED(2, "已完成"),
    ARCHIVED(3, "已归档");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static DailyItemStatusEnum getByCode(Integer code) {
        for (DailyItemStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
