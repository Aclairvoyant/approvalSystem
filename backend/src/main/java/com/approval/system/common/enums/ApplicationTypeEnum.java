package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 申请类型（applications.app_type）
 */
@Getter
@AllArgsConstructor
public enum ApplicationTypeEnum {
    NORMAL(1, "普通申请"),
    VOICE(2, "语音申请");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static ApplicationTypeEnum getByCode(Integer code) {
        for (ApplicationTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
