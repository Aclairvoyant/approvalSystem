package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotifyTypeEnum {
    SMS(1, "短信"),
    EMAIL(2, "邮件"),
    PUSH(3, "APP推送");

    private final Integer code;
    private final String desc;

    public static NotifyTypeEnum getByCode(Integer code) {
        for (NotifyTypeEnum type : NotifyTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
