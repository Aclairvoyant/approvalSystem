package com.approval.system.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 语音申请音频上传状态（applications.voice_status）
 */
@Getter
@AllArgsConstructor
public enum VoiceStatusEnum {
    NONE(0, "无语音"),
    UPLOADING(1, "上传中"),
    READY(2, "已就绪"),
    FAILED(3, "上传失败");

    private final Integer code;
    private final String desc;

    /**
     * 判断数据库里的数值是否等于当前枚举值（null 安全）
     */
    public boolean is(Integer value) {
        return code.equals(value);
    }

    public static VoiceStatusEnum getByCode(Integer code) {
        for (VoiceStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
