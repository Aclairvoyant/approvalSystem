package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * application_comments.content 为 TEXT，这里限制 5000 字。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {

    @NotNull(message = "申请ID不能为空")
    private Long applicationId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 5000, message = "评论内容不能超过5000个字符")
    private String content;

    /** 回复的父评论 ID，顶级评论为空 */
    private Long parentId;
}
