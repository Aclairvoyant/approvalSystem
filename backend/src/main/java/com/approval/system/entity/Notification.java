package com.approval.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("notifications")
public class Notification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("notify_user_id")
    private Long notifyUserId;

    @TableField("notify_type")
    private Integer notifyType;

    @TableField("notify_title")
    private String notifyTitle;

    @TableField("notify_content")
    private String notifyContent;

    @TableField("phone")
    private String phone;

    @TableField("email")
    private String email;

    @TableField("send_status")
    private Integer sendStatus;

    @TableField("send_error")
    private String sendError;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("sent_at")
    private LocalDateTime sentAt;
}
