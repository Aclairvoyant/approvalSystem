package com.approval.system.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplicationApprovalRequest {

    /** 审批意见 / 驳回原因，落库到 operation_logs.operation_detail 与 applications.reject_reason（均为 VARCHAR(500)） */
    @Size(max = 500, message = "审批意见不能超过500个字符")
    private String approvalDetail;
}
