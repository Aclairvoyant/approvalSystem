package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationCreateRequest;
import com.approval.system.dto.ApplicationApprovalRequest;
import com.approval.system.entity.Application;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private IOperationLogService operationLogService;

    /**
     * 创建申请单
     */
    @PostMapping
    public ApiResponse<Application> createApplication(@RequestBody ApplicationCreateRequest request) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            // 验证双方是否互为对象
            if (!userRelationService.isRelated(userId, request.getApproverId())) {
                return ApiResponse.fail(400, "只有互为对象的用户才能创建申请");
            }

            Application application = applicationService.createApplication(
                    userId, request.getApproverId(), request.getTitle(),
                    request.getDescription(), request.getRemark(), request.getSendVoiceNotification());

            return ApiResponse.success("申请创建成功", application);
        } catch (Exception e) {
            log.error("创建申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 手动发送语音通知给审批人
     */
    @PostMapping("/{id}/send-voice-notification")
    public ApiResponse<Void> sendVoiceNotification(@PathVariable Long id) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            boolean success = applicationService.sendVoiceNotificationToApprover(id, userId);

            if (success) {
                return ApiResponse.success("语音通知发送成功");
            } else {
                return ApiResponse.fail(400, "语音通知发送失败，请检查审批人手机号是否填写或申请状态是否为待审批");
            }
        } catch (Exception e) {
            log.error("发送语音通知失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 审批通过
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveApplication(@PathVariable Long id,
                                                @RequestBody ApplicationApprovalRequest request) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            applicationService.approveApplication(id, userId, request.getApprovalDetail());
            return ApiResponse.success("审批通过");
        } catch (Exception e) {
            log.error("审批失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 驳回申请
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectApplication(@PathVariable Long id,
                                               @RequestBody ApplicationApprovalRequest request) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            applicationService.rejectApplication(id, userId, request.getApprovalDetail());
            return ApiResponse.success("申请已驳回");
        } catch (Exception e) {
            log.error("驳回失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取申请人的申请列表
     */
    @GetMapping("/my-applications")
    public ApiResponse<Page<Application>> getMyApplications(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Page<Application> page = applicationService.getApplicantApplications(userId, status, pageNum, pageSize);
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取申请列表失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取待审批列表
     */
    @GetMapping("/pending")
    public ApiResponse<Page<Application>> getPendingApplications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Page<Application> page = applicationService.getApproverPendingApplications(userId, pageNum, pageSize);
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取待审批列表失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取申请详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Application> getApplicationDetail(@PathVariable Long id) {
        try {
            Application application = applicationService.getApplicationDetail(id);
            if (application == null) {
                return ApiResponse.fail(404, "申请不存在");
            }
            return ApiResponse.success(application);
        } catch (Exception e) {
            log.error("获取申请详情失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 更新申请（仅待审批状态可修改）
     */
    @PutMapping("/{id}")
    public ApiResponse<Application> updateApplication(@PathVariable Long id,
                                                       @RequestBody ApplicationCreateRequest request) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Application application = applicationService.updateApplication(
                    id, userId, request.getTitle(), request.getDescription(), request.getRemark());
            return ApiResponse.success("修改成功", application);
        } catch (Exception e) {
            log.error("修改申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 取消/撤回申请（仅待审批状态可取消）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelApplication(@PathVariable Long id) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            applicationService.cancelApplication(id, userId);
            return ApiResponse.success("申请已取消");
        } catch (Exception e) {
            log.error("取消申请失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取我审批过的申请列表
     */
    @GetMapping("/my-approvals")
    public ApiResponse<Page<Application>> getMyApprovals(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Page<Application> page = applicationService.getMyApprovedApplications(userId, status, pageNum, pageSize);
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取审批记录失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
