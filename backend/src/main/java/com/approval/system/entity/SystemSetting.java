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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("system_settings")
public class SystemSetting {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("setting_key")
    private String settingKey;

    @TableField("setting_value")
    private String settingValue;

    @TableField("`sensitive`")
    private Boolean sensitive;

    @TableField("description")
    private String description;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
