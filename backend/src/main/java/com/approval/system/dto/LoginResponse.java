package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    /**
     * 用户角色: 0=普通用户, 1=管理员
     */
    private Integer role;
    /**
     * 是否启用语音通知权限
     */
    private Boolean voiceNotificationEnabled;
}
