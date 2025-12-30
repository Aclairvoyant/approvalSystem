package com.approval.system.service.impl;

import com.approval.system.entity.OperationLog;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.service.IOperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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
}
