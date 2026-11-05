package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 长度与 application_templates 表一致：title VARCHAR(255)，description TEXT，remark VARCHAR(500)。
 */
@Data
public class ApplicationTemplateRequest {

    private Long partnerId;

    @NotBlank(message = "请输入模板标题")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @NotBlank(message = "请输入模板说明")
    @Size(max = 5000, message = "说明不能超过5000个字符")
    private String description;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    private Boolean shared;
}
