package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("couple_events")
public class CoupleEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("event_type")
    private Integer eventType;

    @TableField("title")
    private String title;

    @TableField("event_date")
    private LocalDate eventDate;

    @TableField("repeat_type")
    private Integer repeatType;

    @TableField("remind_days_before")
    private Integer remindDaysBefore;

    @TableField("note")
    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
