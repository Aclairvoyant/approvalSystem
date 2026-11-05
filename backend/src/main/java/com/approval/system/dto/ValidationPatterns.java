package com.approval.system.dto;

/**
 * 请求 DTO 共用的校验规则，和前端表单规则保持一致。
 */
public final class ValidationPatterns {

    /** 中国大陆手机号，与前端 PHONE_PATTERN 一致 */
    public static final String PHONE = "^1[3-9]\\d{9}$";

    /** 发送/校验邮箱验证码时沿用的邮箱格式（与改造前 AuthController 中的正则一致） */
    public static final String EMAIL = "^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$";

    private ValidationPatterns() {
    }
}
