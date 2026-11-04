package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户账号状态（users.status）
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static UserStatusEnum getByCode(Integer code) {
        for (UserStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
