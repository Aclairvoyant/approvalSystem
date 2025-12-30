package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OperationTypeEnum {
    CREATE(1, "创建"),
    APPROVE(2, "审批通过"),
    REJECT(3, "驳回"),
    MODIFY(4, "修改"),
    CANCEL(5, "取消");

    private final Integer code;
    private final String desc;

    public static OperationTypeEnum getByCode(Integer code) {
        for (OperationTypeEnum type : OperationTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
