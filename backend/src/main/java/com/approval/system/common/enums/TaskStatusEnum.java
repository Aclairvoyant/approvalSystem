package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskStatusEnum {
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    ABANDONED(3, "已放弃"),
    TIMEOUT(4, "已超时");

    private final Integer code;
    private final String desc;

    public static TaskStatusEnum getByCode(Integer code) {
        for (TaskStatusEnum status : TaskStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
