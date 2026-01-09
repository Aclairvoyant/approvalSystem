package com.approval.system.common.enums;

import lombok.Getter;

/**
 * 麻将规则类型枚举
 */
@Getter
public enum MahjongRuleType {
    QIAO_MA(1, "敲麻", "上海敲麻规则，只能自摸胡牌"),
    BAI_DA(2, "百搭", "上海百搭规则，带百搭牌");

    private final int code;
    private final String name;
    private final String description;

    MahjongRuleType(int code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public static MahjongRuleType fromCode(int code) {
        for (MahjongRuleType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return QIAO_MA;
    }
}
