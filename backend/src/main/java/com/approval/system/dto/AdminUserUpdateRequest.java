package com.approval.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员修改用户资料：字段为 null 表示不修改；email 传空字符串表示清空。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserUpdateRequest {

    @Size(max = 50, message = "姓名不能超过50个字符")
    private String realName;

    @Pattern(regexp = ValidationPatterns.PHONE, message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;
}
