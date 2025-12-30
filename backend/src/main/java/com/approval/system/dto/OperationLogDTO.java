package com.approval.system.dto;

import com.approval.system.common.enums.OperationTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OperationLogDTO {
    private Long id;
    private Long applicationId;
    private Long operatorId;
    private String operatorName;
    private Integer operationType;
    private String operationTypeDesc;
    private Integer oldStatus;
    private String oldStatusDesc;
    private Integer newStatus;
    private String newStatusDesc;
    private String operationDetail;
    private LocalDateTime createdAt;

    public static OperationLogDTO fromEntity(com.approval.system.entity.OperationLog log,
                                            String operatorName) {
        OperationTypeEnum opType = OperationTypeEnum.getByCode(log.getOperationType());
        return OperationLogDTO.builder()
                .id(log.getId())
                .applicationId(log.getApplicationId())
                .operatorId(log.getOperatorId())
                .operatorName(operatorName)
                .operationType(log.getOperationType())
                .operationTypeDesc(opType != null ? opType.getDesc() : "未知")
                .oldStatus(log.getOldStatus())
                .newStatus(log.getNewStatus())
                .operationDetail(log.getOperationDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
