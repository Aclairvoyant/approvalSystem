package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "请输入用户名")
    private String username;

    /** 登录时不限制长度，避免历史账号因规则变化无法登录 */
    @NotBlank(message = "请输入密码")
    private String password;
}
