package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplicationCreateRequest {
    private Long approverId;
    private String title;
    private String description;
    private String remark;
    /**
     * 是否发送语音通知给审批人（默认为 false）
     */
    private Boolean sendVoiceNotification = false;
}
