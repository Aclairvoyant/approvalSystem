package com.approval.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 与 couple_events 表一致：title VARCHAR(255)，note TEXT；
 * event_type 1=纪念日,2=生日,3=约会,4=其他；repeat_type 0=不重复,1=每年。
 */
@Data
public class CoupleEventRequest {

    private Long partnerId;

    @Min(value = 1, message = "纪念日类型不正确")
    @Max(value = 4, message = "纪念日类型不正确")
    private Integer eventType;

    @NotBlank(message = "请输入纪念日名称")
    @Size(max = 255, message = "名称不能超过255个字符")
    private String title;

    @NotNull(message = "请选择日期")
    private LocalDate eventDate;

    @Min(value = 0, message = "重复方式不正确")
    @Max(value = 1, message = "重复方式不正确")
    private Integer repeatType;

    @Min(value = 0, message = "提前提醒天数不能为负数")
    @Max(value = 365, message = "提前提醒天数不能超过365天")
    private Integer remindDaysBefore;

    @Size(max = 5000, message = "备注不能超过5000个字符")
    private String note;
}
