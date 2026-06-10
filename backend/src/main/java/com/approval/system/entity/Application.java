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
@TableName("applications")
public class Application {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("applicant_id")
    private Long applicantId;

    @TableField("approver_id")
    private Long approverId;

    @TableField("app_type")
    private Integer appType;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("remark")
    private String remark;

    @TableField("voice_transcript")
    private String voiceTranscript;

    @TableField("voice_status")
    private Integer voiceStatus;

    @TableField("voice_upload_error")
    private String voiceUploadError;

    @TableField("status")
    private Integer status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
