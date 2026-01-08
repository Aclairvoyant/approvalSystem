package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskCreateRequest {
    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "任务分类不能为空")
    private String category;

    @NotNull(message = "任务难度不能为空")
    private Integer difficulty;

    private String requirement;

    private Integer timeLimit;

    private Integer points;
}
