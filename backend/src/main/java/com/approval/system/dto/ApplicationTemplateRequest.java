package com.approval.system.dto;

import lombok.Data;

@Data
public class ApplicationTemplateRequest {
    private Long partnerId;
    private String title;
    private String description;
    private String remark;
    private Boolean shared;
}
