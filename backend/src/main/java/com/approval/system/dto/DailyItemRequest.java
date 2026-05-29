package com.approval.system.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyItemRequest {
    private Long partnerId;
    private Integer itemType;
    private String title;
    private String content;
    private Integer priority;
    private LocalDate targetDate;
}
