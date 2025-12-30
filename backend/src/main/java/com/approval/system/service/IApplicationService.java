package com.approval.system.service;

import com.approval.system.entity.Application;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IApplicationService extends IService<Application> {

    /**
     * 创建申请单
     */
    Application createApplication(Long applicantId, Long approverId, String title, String description, String remark, Boolean sendVoiceNotification);

    /**
     * 手动发送语音通知给审批人
     */
    boolean sendVoiceNotificationToApprover(Long applicationId, Long operatorId);

    /**
     * 更新申请单（仅待审批状态可修改）
     */
    Application updateApplication(Long applicationId, Long applicantId, String title, String description, String remark);

    /**
     * 取消/撤回申请（仅待审批状态可撤回）
     */
    void cancelApplication(Long applicationId, Long applicantId);

    /**
     * 审批通过
     */
    void approveApplication(Long applicationId, Long approverId, String approvalDetail);

    /**
     * 驳回申请
     */
    void rejectApplication(Long applicationId, Long approverId, String rejectReason);

    /**
     * 获取申请人的申请列表
     */
    Page<Application> getApplicantApplications(Long applicantId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 获取审批人待审批的列表
     */
    Page<Application> getApproverPendingApplications(Long approverId, Integer pageNum, Integer pageSize);

    /**
     * 获取我审批过的申请列表（已通过/已驳回）
     */
    Page<Application> getMyApprovedApplications(Long approverId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 获取申请详情
     */
    Application getApplicationDetail(Long applicationId);
}
