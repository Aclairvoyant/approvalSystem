package com.approval.system.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CoupleEventRequest {
    private Long partnerId;
    private Integer eventType;
    private String title;
    private LocalDate eventDate;
    private Integer repeatType;
    private Integer remindDaysBefore;
    private String note;
}
