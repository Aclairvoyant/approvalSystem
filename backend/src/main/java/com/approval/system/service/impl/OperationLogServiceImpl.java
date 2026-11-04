package com.approval.system.service.impl;

import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.entity.OperationLog;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.service.IOperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements IOperationLogService {

    @Override
    public Page<OperationLog> getApplicationTimeline(Long applicationId, Integer pageNum, Integer pageSize) {
        QueryWrapper<OperationLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("application_id", applicationId);
        queryWrapper.orderByAsc("created_at");

        Page<OperationLog> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public OperationLog record(Long applicationId, Long operatorId, OperationTypeEnum operationType,
                               Integer oldStatus, Integer newStatus, String operationDetail) {
        OperationLog operationLog = OperationLog.builder()
                .applicationId(applicationId)
                .operatorId(operatorId)
                .operationType(operationType.getCode())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .operationDetail(operationDetail)
                .createdAt(LocalDateTime.now())
                .build();
        this.baseMapper.insert(operationLog);
        return operationLog;
    }
}
