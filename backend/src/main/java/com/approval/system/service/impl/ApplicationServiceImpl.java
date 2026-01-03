package com.approval.system.service.impl;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.entity.Application;
import com.approval.system.entity.OperationLog;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IEmailService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.IVoiceNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, Application> implements IApplicationService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IVoiceNotificationService voiceNotificationService;

    @Autowired
    private IEmailService emailService;

    @Autowired
    private INotificationService notificationService;

    @Override
    @Transactional
    public Application createApplication(Long applicantId, Long approverId, String title, String description, String remark, Boolean sendVoiceNotification) {
        Application application = Application.builder()
                .applicantId(applicantId)
                .approverId(approverId)
                .title(title)
                .description(description)
                .remark(remark)
                .status(ApplicationStatusEnum.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(application);

        // 记录操作日志
        recordOperationLog(application.getId(), applicantId, OperationTypeEnum.CREATE.getCode(),
                null, ApplicationStatusEnum.PENDING.getCode(), "创建申请单");

        // 根据开关决定是否发送语音通知给审批人
        if (Boolean.TRUE.equals(sendVoiceNotification)) {
            try {
                // 检查申请人是否有语音通知权限
                User applicant = userMapper.selectById(applicantId);
                if (applicant == null) {
                    log.error("申请人不存在，applicantId: {}", applicantId);
                    throw new RuntimeException("申请人不存在");
                }

                if (applicant.getVoiceNotificationEnabled() == null || !applicant.getVoiceNotificationEnabled()) {
                    log.warn("用户未开通语音通知权限，userId: {}", applicantId);
                    throw new RuntimeException("您未开通语音通知权限，请联系管理员开通");
                }

                // 获取审批人信息
                User approver = userMapper.selectById(approverId);

                if (approver != null) {
                    String applicantName = applicant.getRealName() != null
                            ? applicant.getRealName()
                            : applicant.getUsername();

                    // 发送语音通知
                    if (approver.getPhone() != null && !approver.getPhone().isEmpty()) {
                        boolean voiceSuccess = voiceNotificationService.notifyApproverNewApplication(
                                approver.getPhone(),
                                applicantName,
                                title
                        );
                        // 创建通知记录 - 语音通知（已发送状态）
                        notificationService.createSentNotification(
                                application.getId(),
                                approverId,
                                1, // 1=SMS(语音通知)
                                "新的待审批申请",
                                "您有来自 " + applicantName + " 的待审批申请: " + title,
                                approver.getPhone(),
                                null,
                                voiceSuccess
                        );
                        if (voiceSuccess) {
                            log.info("语音通知发送成功，手机号: {}", approver.getPhone());
                        } else {
                            log.warn("语音通知发送失败，手机号: {}", approver.getPhone());
                        }
                    } else {
                        log.warn("审批人手机号为空，无法发送语音通知，审批人ID: {}", approverId);
                    }
                }
            } catch (Exception e) {
                // 通知发送失败不影响主流程
                log.error("发送通知失败，但申请创建成功", e);
            }
        }

        // 默认发送邮件通知
        // 发送邮件通知
        try {
            // 获取申请人和审批人信息
            User applicant = userMapper.selectById(applicantId);
            User approver = userMapper.selectById(approverId);

            if (approver != null) {
                String applicantName = applicant != null && applicant.getRealName() != null
                        ? applicant.getRealName()
                        : (applicant != null ? applicant.getUsername() : "用户");

                if (approver.getEmail() != null && !approver.getEmail().isEmpty()) {
                    boolean emailSuccess = emailService.sendApplicationNotification(
                            approver.getEmail(),
                            applicantName,
                            title,
                            application.getId()
                    );
                    // 创建通知记录 - 邮件通知（已发送状态）
                    notificationService.createSentNotification(
                            application.getId(),
                            approverId,
                            2, // 2=EMAIL
                            "新的待审批申请",
                            "您有来自 " + applicantName + " 的待审批申请: " + title,
                            null,
                            approver.getEmail(),
                            emailSuccess
                    );
                    if (emailSuccess) {
                        log.info("邮件通知发送成功，邮箱: {}", approver.getEmail());
                    } else {
                        log.warn("邮件通知发送失败，邮箱: {}", approver.getEmail());
                    }
                } else {
                    log.warn("审批人邮箱为空，无法发送邮件通知，审批人ID: {}", approverId);
                }
            }

        } catch (Exception e) {
            // 通知发送失败不影响主流程
            log.error("发送通知失败，但申请创建成功", e);
        }


        return application;
    }

    @Override
    public boolean sendVoiceNotificationToApprover(Long applicationId, Long operatorId) {
        try {
            // 检查操作者是否有语音通知权限
            User operator = userMapper.selectById(operatorId);
            if (operator == null) {
                log.error("操作者不存在，operatorId: {}", operatorId);
                throw new RuntimeException("操作者不存在");
            }

            if (operator.getVoiceNotificationEnabled() == null || !operator.getVoiceNotificationEnabled()) {
                log.warn("用户未开通语音通知权限，userId: {}", operatorId);
                throw new RuntimeException("您未开通语音通知权限，请联系管理员开通");
            }

            // 获取申请详情
            Application application = this.getById(applicationId);
            if (application == null) {
                log.error("申请不存在，applicationId: {}", applicationId);
                return false;
            }

            // 只有待审批状态才能发送语音通知
            if (!ApplicationStatusEnum.PENDING.getCode().equals(application.getStatus())) {
                log.warn("只有待审批状态的申请才能发送语音通知，当前状态: {}", application.getStatus());
                return false;
            }

            // 获取申请人和审批人信息
            User applicant = userMapper.selectById(application.getApplicantId());
            User approver = userMapper.selectById(application.getApproverId());

            if (approver == null || approver.getPhone() == null || approver.getPhone().isEmpty()) {
                log.warn("审批人手机号为空，无法发送语音通知，审批人ID: {}", application.getApproverId());
                return false;
            }

            String applicantName = applicant != null && applicant.getRealName() != null
                    ? applicant.getRealName()
                    : (applicant != null ? applicant.getUsername() : "用户");

            // 发送语音通知
            boolean success = voiceNotificationService.notifyApproverNewApplication(
                    approver.getPhone(),
                    applicantName,
                    application.getTitle()
            );

            // 创建通知记录 - 语音通知（已发送状态）
            notificationService.createSentNotification(
                    application.getId(),
                    application.getApproverId(),
                    1, // 1=SMS(语音通知)
                    "新的待审批申请",
                    "您有来自 " + applicantName + " 的待审批申请: " + application.getTitle(),
                    approver.getPhone(),
                    null,
                    success
            );

            if (success) {
                log.info("手动触发语音通知成功，applicationId: {}, 手机号: {}, 操作人: {}",
                        applicationId, approver.getPhone(), operatorId);
            } else {
                log.warn("手动触发语音通知失败，applicationId: {}, 手机号: {}",
                        applicationId, approver.getPhone());
            }

            return success;
        } catch (Exception e) {
            log.error("手动触发语音通知异常，applicationId: {}", applicationId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public Application updateApplication(Long applicationId, Long applicantId, String title, String description, String remark) {
        Application application = this.getById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请单不存在");
        }

        if (!application.getApplicantId().equals(applicantId)) {
            throw new RuntimeException("没有权限修改此申请");
        }

        if (!ApplicationStatusEnum.PENDING.getCode().equals(application.getStatus())) {
            throw new RuntimeException("只有待审批的申请可以修改");
        }

        // 更新申请信息
        application.setTitle(title);
        application.setDescription(description);
        application.setRemark(remark);
        application.setUpdatedAt(LocalDateTime.now());

        this.updateById(application);

        // 记录操作日志
        recordOperationLog(applicationId, applicantId, OperationTypeEnum.MODIFY.getCode(),
                ApplicationStatusEnum.PENDING.getCode(), ApplicationStatusEnum.PENDING.getCode(),
                "修改申请内容");

        return application;
    }

    @Override
    @Transactional
    public void cancelApplication(Long applicationId, Long applicantId) {
        Application application = this.getById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请单不存在");
        }

        if (!application.getApplicantId().equals(applicantId)) {
            throw new RuntimeException("没有权限取消此申请");
        }

        if (!ApplicationStatusEnum.PENDING.getCode().equals(application.getStatus())) {
            throw new RuntimeException("只有待审批的申请可以取消");
        }

        Integer oldStatus = application.getStatus();
        application.setStatus(ApplicationStatusEnum.CANCELLED.getCode());
        application.setUpdatedAt(LocalDateTime.now());

        this.updateById(application);

        // 记录操作日志
        recordOperationLog(applicationId, applicantId, OperationTypeEnum.CANCEL.getCode(),
                oldStatus, ApplicationStatusEnum.CANCELLED.getCode(), "取消申请");
    }

    @Override
    @Transactional
    public void approveApplication(Long applicationId, Long approverId, String approvalDetail) {
        Application application = this.getById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请单不存在");
        }

        if (!application.getApproverId().equals(approverId)) {
            throw new RuntimeException("没有权限审批此申请");
        }

        Integer oldStatus = application.getStatus();
        application.setStatus(ApplicationStatusEnum.APPROVED.getCode());
        application.setApprovedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());

        this.updateById(application);

        // 记录操作日志
        recordOperationLog(applicationId, approverId, OperationTypeEnum.APPROVE.getCode(),
                oldStatus, ApplicationStatusEnum.APPROVED.getCode(), approvalDetail);

        // 发送邮件通知申请人
        try {
            User applicant = userMapper.selectById(application.getApplicantId());
            User approver = userMapper.selectById(approverId);

            if (applicant != null && applicant.getEmail() != null && !applicant.getEmail().isEmpty()) {
                String applicantName = applicant.getRealName() != null ? applicant.getRealName() : applicant.getUsername();
                String approverName = approver != null && approver.getRealName() != null
                        ? approver.getRealName()
                        : (approver != null ? approver.getUsername() : "审批人");

                boolean emailSuccess = emailService.sendApprovalNotification(
                        applicant.getEmail(),
                        applicantName,
                        approverName,
                        application.getTitle(),
                        approvalDetail,
                        applicationId
                );

                // 创建通知记录（已发送状态）
                String emailTitle = "申请已批准 - " + application.getTitle();
                String emailContent = "您提交的申请已被批准";
                notificationService.createSentNotification(
                        application.getId(),
                        application.getApplicantId(),
                        2, // 2=EMAIL
                        emailTitle,
                        emailContent,
                        null,
                        applicant.getEmail(),
                        emailSuccess
                );

                if (emailSuccess) {
                    log.info("批准通知邮件发送成功，applicationId: {}, 邮箱: {}", applicationId, applicant.getEmail());
                } else {
                    log.warn("批准通知邮件发送失败，applicationId: {}, 邮箱: {}", applicationId, applicant.getEmail());
                }
            } else {
                log.warn("申请人邮箱为空，无法发送批准通知，申请人ID: {}", application.getApplicantId());
            }
        } catch (Exception e) {
            log.error("发送批准通知失败，但审批操作已完成", e);
        }
    }

    @Override
    @Transactional
    public void rejectApplication(Long applicationId, Long approverId, String rejectReason) {
        Application application = this.getById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请单不存在");
        }

        if (!application.getApproverId().equals(approverId)) {
            throw new RuntimeException("没有权限审批此申请");
        }

        Integer oldStatus = application.getStatus();
        application.setStatus(ApplicationStatusEnum.REJECTED.getCode());
        application.setRejectReason(rejectReason);
        application.setApprovedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());

        this.updateById(application);

        // 记录操作日志
        recordOperationLog(applicationId, approverId, OperationTypeEnum.REJECT.getCode(),
                oldStatus, ApplicationStatusEnum.REJECTED.getCode(), rejectReason);

        // 发送邮件通知申请人
        try {
            User applicant = userMapper.selectById(application.getApplicantId());
            User approver = userMapper.selectById(approverId);

            if (applicant != null && applicant.getEmail() != null && !applicant.getEmail().isEmpty()) {
                String applicantName = applicant.getRealName() != null ? applicant.getRealName() : applicant.getUsername();
                String approverName = approver != null && approver.getRealName() != null
                        ? approver.getRealName()
                        : (approver != null ? approver.getUsername() : "审批人");

                boolean emailSuccess = emailService.sendRejectionNotification(
                        applicant.getEmail(),
                        applicantName,
                        approverName,
                        application.getTitle(),
                        rejectReason,
                        applicationId
                );

                // 创建通知记录（已发送状态）
                String emailTitle = "申请已驳回 - " + application.getTitle();
                String emailContent = "您提交的申请已被驳回";
                notificationService.createSentNotification(
                        application.getId(),
                        application.getApplicantId(),
                        2, // 2=EMAIL
                        emailTitle,
                        emailContent,
                        null,
                        applicant.getEmail(),
                        emailSuccess
                );

                if (emailSuccess) {
                    log.info("驳回通知邮件发送成功，applicationId: {}, 邮箱: {}", applicationId, applicant.getEmail());
                } else {
                    log.warn("驳回通知邮件发送失败，applicationId: {}, 邮箱: {}", applicationId, applicant.getEmail());
                }
            } else {
                log.warn("申请人邮箱为空，无法发送驳回通知，申请人ID: {}", application.getApplicantId());
            }
        } catch (Exception e) {
            log.error("发送驳回通知失败，但审批操作已完成", e);
        }
    }

    @Override
    public Page<Application> getApplicantApplications(Long applicantId, Integer status, Integer pageNum, Integer pageSize) {
        QueryWrapper<Application> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("applicant_id", applicantId);

        if (status != null) {
            queryWrapper.eq("status", status);
        }

        queryWrapper.orderByDesc("created_at");

        Page<Application> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public Page<Application> getApproverPendingApplications(Long approverId, Integer pageNum, Integer pageSize) {
        QueryWrapper<Application> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("approver_id", approverId);
        queryWrapper.eq("status", ApplicationStatusEnum.PENDING.getCode());
        queryWrapper.orderByDesc("created_at");

        Page<Application> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public Page<Application> getMyApprovedApplications(Long approverId, Integer status, Integer pageNum, Integer pageSize) {
        QueryWrapper<Application> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("approver_id", approverId);

        // 只查询已审批的（已通过或已驳回）
        if (status != null) {
            queryWrapper.eq("status", status);
        } else {
            // 默认查询已通过和已驳回的
            queryWrapper.in("status", ApplicationStatusEnum.APPROVED.getCode(), ApplicationStatusEnum.REJECTED.getCode());
        }

        queryWrapper.orderByDesc("approved_at");

        Page<Application> page = new Page<>(pageNum, pageSize);
        return this.page(page, queryWrapper);
    }

    @Override
    public Application getApplicationDetail(Long applicationId) {
        return this.getById(applicationId);
    }

    /**
     * 记录操作日志
     */
    private void recordOperationLog(Long applicationId, Long operatorId, Integer operationType,
                                    Integer oldStatus, Integer newStatus, String operationDetail) {
        OperationLog log = OperationLog.builder()
                .applicationId(applicationId)
                .operatorId(operatorId)
                .operationType(operationType)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .operationDetail(operationDetail)
                .createdAt(LocalDateTime.now())
                .build();

        operationLogMapper.insert(log);
    }
}
