package com.approval.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoiceApplicationCreateRequest {

    @NotNull(message = "请选择审批人")
    private Long approverId;
}
