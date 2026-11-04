package com.approval.system.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成任务的请求体。任务记录 ID 取自路径 /record/{recordId}/complete，
 * 前端只提交 completionNote，所以这里的 recordId 不做必填校验。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskCompleteRequest {

    private Long recordId;

    @Size(max = 5000, message = "完成备注不能超过5000个字符")
    private String completionNote;
}
