package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色（users.role）
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {
    USER(0, "普通用户"),
    ADMIN(1, "管理员");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static UserRoleEnum getByCode(Integer code) {
        for (UserRoleEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
