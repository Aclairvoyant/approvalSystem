package com.approval.system.service;

import com.approval.system.entity.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IOperationLogService extends IService<OperationLog> {

    /**
     * 获取申请的操作日志时间线
     */
    Page<OperationLog> getApplicationTimeline(Long applicationId, Integer pageNum, Integer pageSize);
}
