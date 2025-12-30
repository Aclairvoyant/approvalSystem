package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.OperationLogDTO;
import com.approval.system.entity.OperationLog;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    @Autowired
    private IOperationLogService operationLogService;

    @Autowired
    private IUserService userService;

    /**
     * 获取申请的操作时间线
     */
    @GetMapping("/timeline/{applicationId}")
    public ApiResponse<List<OperationLogDTO>> getApplicationTimeline(
            @PathVariable Long applicationId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        try {
            Page<OperationLog> page = operationLogService.getApplicationTimeline(applicationId, pageNum, pageSize);

            List<OperationLogDTO> dtoList = page.getRecords().stream()
                    .map(log -> {
                        String operatorName = userService.getUserById(log.getOperatorId()).getRealName();
                        return OperationLogDTO.fromEntity(log, operatorName);
                    })
                    .collect(Collectors.toList());

            return ApiResponse.success(dtoList);
        } catch (Exception e) {
            log.error("获取操作日志失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
