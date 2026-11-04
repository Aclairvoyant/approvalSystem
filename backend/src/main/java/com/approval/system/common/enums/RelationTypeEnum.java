package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户关系类型（user_relations.relation_type）
 */
@Getter
@AllArgsConstructor
public enum RelationTypeEnum {
    PENDING(1, "申请中"),
    ESTABLISHED(2, "已互为对象");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static RelationTypeEnum getByCode(Integer code) {
        for (RelationTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
