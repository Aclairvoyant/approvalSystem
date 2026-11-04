package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.OperationLogDTO;
import com.approval.system.entity.OperationLog;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Autowired
    private IApplicationService applicationService;

    /**
     * 获取申请的操作时间线
     */
    @GetMapping("/timeline/{applicationId}")
    public ApiResponse<List<OperationLogDTO>> getApplicationTimeline(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        applicationService.assertApplicationParticipantOrAdmin(applicationId, userId);

        Page<OperationLog> page = operationLogService.getApplicationTimeline(applicationId, pageNum, pageSize);

        List<OperationLogDTO> dtoList = page.getRecords().stream()
                .map(log -> {
                    var user = userService.getUserById(log.getOperatorId());
                    String operatorName = user != null ? user.getRealName() : "未知用户";
                    String operatorAvatar = user != null ? user.getAvatar() : null;
                    return OperationLogDTO.fromEntity(log, operatorName, operatorAvatar);
                })
                .collect(Collectors.toList());

        return ApiResponse.success(dtoList);
    }
}
