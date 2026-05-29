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
@TableName("application_templates")
public class ApplicationTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("remark")
    private String remark;

    @TableField("shared")
    private Boolean shared;

    @TableField("usage_count")
    private Integer usageCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
