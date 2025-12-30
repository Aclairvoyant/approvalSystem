package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ApplicationStatusEnum {
    DRAFT(4, "草稿"),
    PENDING(1, "待审批"),
    APPROVED(2, "已批准"),
    REJECTED(3, "已驳回"),
    CANCELLED(5, "已取消");

    private final Integer code;
    private final String desc;

    public static ApplicationStatusEnum getByCode(Integer code) {
        for (ApplicationStatusEnum status : ApplicationStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
