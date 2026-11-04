package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "请提供原密码和新密码")
    private String oldPassword;

    @NotBlank(message = "请提供原密码和新密码")
    @Size(min = 6, max = 64, message = "新密码长度需为6-64位")
    private String newPassword;
}
