package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 长度与 users 表一致：username/real_name VARCHAR(50)，phone VARCHAR(20)，email VARCHAR(100)。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Size(max = 50, message = "用户名不能超过50个字符")
    private String username;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = ValidationPatterns.PHONE, message = "请输入正确的手机号")
    private String phone;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    private String password;

    @Size(max = 100, message = "邮箱不能超过100个字符")
    @Pattern(regexp = ValidationPatterns.EMAIL, message = "邮箱格式不正确")
    private String email;

    @Size(max = 50, message = "姓名不能超过50个字符")
    private String realName;

    /** 邮箱验证码 */
    @Size(max = 10, message = "验证码格式不正确")
    private String emailVerificationCode;
}
