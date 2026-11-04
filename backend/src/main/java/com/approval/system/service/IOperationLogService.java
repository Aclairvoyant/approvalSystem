package com.approval.system.service;

import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.entity.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IOperationLogService extends IService<OperationLog> {

    /**
     * 获取申请的操作日志时间线
     */
    Page<OperationLog> getApplicationTimeline(Long applicationId, Integer pageNum, Integer pageSize);

    /**
     * 记录一条申请操作日志（时间线），返回已写入、带 id 的记录
     */
    OperationLog record(Long applicationId, Long operatorId, OperationTypeEnum operationType,
                        Integer oldStatus, Integer newStatus, String operationDetail);
}
