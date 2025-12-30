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
@TableName("operation_logs")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operation_type")
    private Integer operationType;

    @TableField("old_status")
    private Integer oldStatus;

    @TableField("new_status")
    private Integer newStatus;

    @TableField("operation_detail")
    private String operationDetail;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
