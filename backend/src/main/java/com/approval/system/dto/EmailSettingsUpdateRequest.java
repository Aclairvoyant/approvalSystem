package com.approval.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台修改发件邮箱配置。字段为空表示沿用当前值（由 SystemSettingServiceImpl 处理）。
 */
@Data
public class EmailSettingsUpdateRequest {

    @Size(max = 255, message = "SMTP 服务器地址不能超过255个字符")
    private String host;

    @Min(value = 1, message = "SMTP 端口必须在 1-65535 之间")
    @Max(value = 65535, message = "SMTP 端口必须在 1-65535 之间")
    private Integer port;

    @Size(max = 255, message = "SMTP 用户名不能超过255个字符")
    private String username;

    @Email(message = "发件邮箱格式不正确")
    @Size(max = 255, message = "发件邮箱不能超过255个字符")
    private String fromEmail;

    @Size(max = 500, message = "SMTP 密码不能超过500个字符")
    private String password;

    private Boolean sslEnabled;
}
