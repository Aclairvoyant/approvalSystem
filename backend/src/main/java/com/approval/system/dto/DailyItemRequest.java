package com.approval.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 与 daily_items 表一致：title VARCHAR(255)，content TEXT；
 * item_type 1=待办,2=购物,3=约会计划,4=约定；priority 1=普通,2=重要。
 */
@Data
public class DailyItemRequest {

    private Long partnerId;

    @Min(value = 1, message = "事项类型不正确")
    @Max(value = 4, message = "事项类型不正确")
    private Integer itemType;

    @NotBlank(message = "请输入标题")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @Size(max = 5000, message = "说明不能超过5000个字符")
    private String content;

    @Min(value = 1, message = "优先级不正确")
    @Max(value = 2, message = "优先级不正确")
    private Integer priority;

    private LocalDate targetDate;
}
