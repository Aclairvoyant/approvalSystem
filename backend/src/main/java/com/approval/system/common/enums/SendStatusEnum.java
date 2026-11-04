package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知发送状态（notifications.send_status）
 */
@Getter
@AllArgsConstructor
public enum SendStatusEnum {
    PENDING(1, "待发送"),
    SENT(2, "已发送"),
    FAILED(3, "发送失败");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static SendStatusEnum getByCode(Integer code) {
        for (SendStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
