package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailCodeRequest {

    @NotBlank(message = "请提供邮箱地址")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    @Pattern(regexp = ValidationPatterns.EMAIL, message = "邮箱格式不正确")
    private String email;
}
