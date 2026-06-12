package com.approval.system.service;

import com.approval.system.entity.Application;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

public interface IApplicationService extends IService<Application> {

    /**
     * 创建申请单
     */
    Application createApplication(Long applicantId, Long approverId, String title, String description, String remark, Boolean sendVoiceNotification);

    /**
     * 创建语音消息式申请单
     */
    Application createVoiceApplication(Long applicantId, Long approverId, MultipartFile audio);

    Application createVoiceApplicationDraft(Long applicantId, Long approverId);

    void processVoiceApplicationAudioAsync(Long applicationId, Long applicantId, byte[] audioBytes,
                                           String fileName, String contentType, long fileSize);

    void validateVoiceApplicationAudioUpload(Long applicationId, Long applicantId, MultipartFile audio);

    /**
     * 转写语音申请，已转写时直接返回缓存
     */
    String transcribeVoiceApplication(Long applicationId, Long userId, String language);

    /**
     * 校验当前用户是申请参与人（申请人或审批人）
     */
    void assertApplicationParticipant(Long applicationId, Long userId);

    /**
     * 校验当前用户是申请人
     */
    void assertApplicationApplicant(Long applicationId, Long userId);

    /**
     * 校验当前用户是审批人
     */
    void assertApplicationApprover(Long applicationId, Long userId);

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

    void approveApplication(Long applicationId, Long approverId, String approvalDetail, MultipartFile voiceReply);

    /**
     * 驳回申请
     */
    void rejectApplication(Long applicationId, Long approverId, String rejectReason);

    void rejectApplication(Long applicationId, Long approverId, String rejectReason, MultipartFile voiceReply);

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
