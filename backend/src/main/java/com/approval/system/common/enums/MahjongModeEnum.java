package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上海麻将玩法（mahjong_games.mode）
 */
@Getter
@AllArgsConstructor
public enum MahjongModeEnum {
    BAIDA(1, "百搭"),
    QIAOMA(2, "敲麻");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static MahjongModeEnum getByCode(Integer code) {
        for (MahjongModeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
