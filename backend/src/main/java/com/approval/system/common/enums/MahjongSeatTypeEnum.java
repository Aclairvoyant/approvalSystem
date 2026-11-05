package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 麻将房间座位上坐的是谁（只出现在接口返回里，数据库用 seatN_user_id + bot_mask 表示）
 */
@Getter
@AllArgsConstructor
public enum MahjongSeatTypeEnum {
    EMPTY(0, "空位"),
    HUMAN(1, "真人"),
    BOT(2, "电脑");

    private final Integer code;
    private final String desc;
}
