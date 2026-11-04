package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 长度与 applications 表一致：title VARCHAR(255)，remark VARCHAR(500)，description LONGTEXT（这里限制 10000 字）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplicationCreateRequest {

    @NotNull(message = "请选择审批人")
    private Long approverId;

    @NotBlank(message = "请输入标题")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @Size(max = 10000, message = "说明不能超过10000个字符")
    private String description;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    /**
     * 是否发送语音通知给审批人（默认为 false）
     */
    @Builder.Default
    private Boolean sendVoiceNotification = false;
}
