package com.approval.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255, message = "任务标题不能超过255个字符")
    private String title;

    @Size(max = 5000, message = "任务描述不能超过5000个字符")
    private String description;

    @NotBlank(message = "任务分类不能为空")
    @Size(max = 50, message = "任务分类不能超过50个字符")
    private String category;

    @NotNull(message = "任务难度不能为空")
    @Min(value = 1, message = "任务难度需在1-3之间")
    @Max(value = 3, message = "任务难度需在1-3之间")
    private Integer difficulty;

    @Size(max = 5000, message = "完成要求不能超过5000个字符")
    private String requirement;

    @Min(value = 0, message = "完成时限不能为负数")
    private Integer timeLimit;

    @Min(value = 0, message = "任务积分不能为负数")
    private Integer points;
}
