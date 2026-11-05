package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改待审批申请。审批人不可修改，所以没有 approverId（前端仍可能带上，会被忽略）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplicationUpdateRequest {

    @NotBlank(message = "请输入标题")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @Size(max = 10000, message = "说明不能超过10000个字符")
    private String description;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
