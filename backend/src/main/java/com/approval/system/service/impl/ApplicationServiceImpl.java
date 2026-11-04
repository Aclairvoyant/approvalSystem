package com.approval.system.service.impl;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.ApplicationTypeEnum;
import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.common.enums.VoiceStatusEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.OperationLog;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IApprovalAttachmentService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.notification.ApplicationDecidedEvent;
import com.approval.system.service.notification.ApplicationNotifier;
import com.approval.system.service.notification.ApplicationSubmittedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 申请单的状态流转、查询和访问校验。
 * <p>
 * 协作者：
 * <ul>
 *     <li>{@link VoiceApplicationProcessor}：语音申请的草稿、音频上传和转写；</li>
 *     <li>{@link ApplicationNotifier}：邮件 / 语音通知。状态变更只发布事件
 *     （{@link ApplicationSubmittedEvent}、{@link ApplicationDecidedEvent}），通知在事务提交后异步发送。</li>
 * </ul>
 */
@Slf4j
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, Application> implements IApplicationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IOperationLogService operationLogService;

    @Autowired
    private IApprovalAttachmentService approvalAttachmentService;

    @Autowired
    private VoiceApplicationProcessor voiceApplicationProcessor;

    @Autowired
    private ApplicationNotifier applicationNotifier;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Application createApplication(Long applicantId, Long approverId, String title, String description, String remark, Boolean sendVoiceNotification) {
        Application application = Application.builder()
                .applicantId(applicantId)
                .approverId(approverId)
                .appType(ApplicationTypeEnum.NORMAL.getCode())
                .voiceStatus(VoiceStatusEnum.NONE.getCode())
                .title(title)
                .description(description)
                .remark(remark)
                .status(ApplicationStatusEnum.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(application);

        operationLogService.record(application.getId(), applicantId, OperationTypeEnum.CREATE,
                null, ApplicationStatusEnum.PENDING.getCode(), "创建申请单");

        // 邮件（以及按开关发送的语音通知）在事务提交后异步发送，失败不影响申请创建
        eventPublisher.publishEvent(new ApplicationSubmittedEvent(
                application.getId(), applicantId, approverId, title, Boolean.TRUE.equals(sendVoiceNotification)));
        return application;
    }

    @Override
    @Transactional
    public Application createVoiceApplication(Long applicantId, Long approverId, MultipartFile audio) {
        return voiceApplicationProcessor.createWithAudio(applicantId, approverId, audio);
    }

    @Override
    @Transactional
    public Application createVoiceApplicationDraft(Long applicantId, Long approverId) {
        return voiceApplicationProcessor.createDraft(applicantId, approverId);
    }

    /**
     * 立即返回；上传在 {@link VoiceApplicationProcessor#processAudioAsync} 的异步线程里执行
     */
    @Override
    public void processVoiceApplicationAudioAsync(Long applicationId, Long applicantId, byte[] audioBytes,
                                                  String fileName, String contentType, long fileSize) {
        voiceApplicationProcessor.processAudioAsync(applicationId, applicantId, audioBytes, fileName, contentType, fileSize);
    }

    @Override
    @Transactional
    public void validateVoiceApplicationAudioUpload(Long applicationId, Long applicantId, MultipartFile audio) {
        voiceApplicationProcessor.prepareUpload(applicationId, applicantId, audio);
    }

    /**
     * 不加事务：中间要下载音频并调用语音识别接口，只有最后一次写库
     */
    @Override
    public String transcribeVoiceApplication(Long applicationId, Long userId, String language) {
        Application application = requireApplication(applicationId);
        assertParticipant(application, userId);
        return voiceApplicationProcessor.transcribe(application, language);
    }

    @Override
    public void assertApplicationParticipant(Long applicationId, Long userId) {
        assertParticipant(requireApplication(applicationId), userId);
    }

    @Override
    public void assertApplicationParticipantOrAdmin(Long applicationId, Long userId) {
        Application application = requireApplication(applicationId);
        if (isParticipant(application, userId)) {
            return;
        }
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("无权访问此申请");
        }
    }

    @Override
    public void assertApplicationApplicant(Long applicationId, Long userId) {
        Application application = requireApplication(applicationId);
        if (!application.getApplicantId().equals(userId)) {
            throw BusinessException.forbidden("无权操作此申请");
        }
    }

    @Override
    public void assertApplicationApprover(Long applicationId, Long userId) {
        Application application = requireApplication(applicationId);
        if (!application.getApproverId().equals(userId)) {
            throw BusinessException.forbidden("无权审批此申请");
        }
    }

    @Override
    public boolean sendVoiceNotificationToApprover(Long applicationId, Long operatorId) {
        Application application = this.getById(applicationId);
        if (application == null) {
            log.error("申请不存在，applicationId: {}", applicationId);
            return false;
        }
        // 只有申请人本人能催审批人接电话，否则任何开通了语音权限的用户都能给别人的审批人打电话
        if (!application.getApplicantId().equals(operatorId)) {
            throw BusinessException.forbidden("无权操作此申请");
        }
        return applicationNotifier.sendVoiceNotificationToApprover(application, operatorId);
    }

    @Override
    @Transactional
    public Application updateApplication(Long applicationId, Long applicantId, String title, String description, String remark) {
        Application application = requireApplication(applicationId);
        if (!application.getApplicantId().equals(applicantId)) {
            throw BusinessException.forbidden("没有权限修改此申请");
        }
        if (!ApplicationStatusEnum.PENDING.is(application.getStatus())) {
            throw BusinessException.badRequest("只有待审批的申请可以修改");
        }

        application.setTitle(title);
        application.setDescription(description);
        application.setRemark(remark);
        application.setUpdatedAt(LocalDateTime.now());
        this.updateById(application);

        operationLogService.record(applicationId, applicantId, OperationTypeEnum.MODIFY,
                ApplicationStatusEnum.PENDING.getCode(), ApplicationStatusEnum.PENDING.getCode(), "修改申请内容");
        return application;
    }

    @Override
    @Transactional
    public void cancelApplication(Long applicationId, Long applicantId) {
        Application application = requireApplication(applicationId);
        if (!application.getApplicantId().equals(applicantId)) {
            throw BusinessException.forbidden("没有权限取消此申请");
        }
        if (!ApplicationStatusEnum.PENDING.is(application.getStatus())) {
            throw BusinessException.badRequest("只有待审批的申请可以取消");
        }

        Integer oldStatus = application.getStatus();
        application.setStatus(ApplicationStatusEnum.CANCELLED.getCode());
        application.setUpdatedAt(LocalDateTime.now());
        this.updateById(application);

        operationLogService.record(applicationId, applicantId, OperationTypeEnum.CANCEL,
                oldStatus, ApplicationStatusEnum.CANCELLED.getCode(), "取消申请");
    }

    @Override
    @Transactional
    public void approveApplication(Long applicationId, Long approverId, String approvalDetail) {
        approveApplication(applicationId, approverId, approvalDetail, null);
    }

    @Override
    @Transactional
    public void approveApplication(Long applicationId, Long approverId, String approvalDetail, MultipartFile voiceReply) {
        Application application = requireApplication(applicationId);
        assertAssignedApprover(application, approverId);
        decide(application, approverId, true, approvalDetail, voiceReply);
    }

    @Override
    @Transactional
    public void adminApproveApplication(Long applicationId, Long adminId, String approvalDetail) {
        decide(requireApplication(applicationId), adminId, true, approvalDetail, null);
    }

    @Override
    @Transactional
    public void rejectApplication(Long applicationId, Long approverId, String rejectReason) {
        rejectApplication(applicationId, approverId, rejectReason, null);
    }

    @Override
    @Transactional
    public void rejectApplication(Long applicationId, Long approverId, String rejectReason, MultipartFile voiceReply) {
        Application application = requireApplication(applicationId);
        assertAssignedApprover(application, approverId);
        decide(application, approverId, false, rejectReason, voiceReply);
    }

    @Override
    @Transactional
    public void adminRejectApplication(Long applicationId, Long adminId, String rejectReason) {
        decide(requireApplication(applicationId), adminId, false, rejectReason, null);
    }

    /**
     * 批准 / 驳回的公共流程。operatorId 是指定审批人或代为审批的管理员，操作日志记在他名下。
     */
    private void decide(Application application, Long operatorId, boolean approved, String detail, MultipartFile voiceReply) {
        Long applicationId = application.getId();
        if (!ApplicationStatusEnum.PENDING.is(application.getStatus())) {
            throw BusinessException.badRequest("只有待处理的申请才能被批准或拒绝");
        }
        if (voiceReply != null) {
            voiceApplicationProcessor.validateAudio(voiceReply);
        }

        Integer oldStatus = application.getStatus();
        ApplicationStatusEnum newStatus = approved ? ApplicationStatusEnum.APPROVED : ApplicationStatusEnum.REJECTED;
        application.setStatus(newStatus.getCode());
        if (!approved) {
            application.setRejectReason(detail);
        }
        application.setApprovedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        this.updateById(application);

        OperationLog operationLog = operationLogService.record(applicationId, operatorId,
                approved ? OperationTypeEnum.APPROVE : OperationTypeEnum.REJECT,
                oldStatus, newStatus.getCode(), detail);
        if (voiceReply != null) {
            approvalAttachmentService.uploadApprovalAttachment(applicationId, operatorId, operationLog.getId(), voiceReply);
        }

        eventPublisher.publishEvent(new ApplicationDecidedEvent(
                applicationId, application.getApplicantId(), operatorId, application.getTitle(), approved, detail));
    }

    @Override
    public Page<Application> getApplicantApplications(Long applicantId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Application> queryWrapper = new LambdaQueryWrapper<Application>()
                .eq(Application::getApplicantId, applicantId)
                .eq(status != null, Application::getStatus, status)
                .orderByDesc(Application::getCreatedAt);
        return pageWithNames(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public Page<Application> getApproverPendingApplications(Long approverId, Integer pageNum, Integer pageSize) {
        return getApproverPendingApplications(approverId, null, pageNum, pageSize);
    }

    @Override
    public Page<Application> getApproverPendingApplications(Long approverId, String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Application> queryWrapper = new LambdaQueryWrapper<Application>()
                .eq(Application::getApproverId, approverId)
                .eq(Application::getStatus, ApplicationStatusEnum.PENDING.getCode())
                .like(StringUtils.hasText(keyword), Application::getTitle, keyword == null ? null : keyword.trim())
                .orderByDesc(Application::getCreatedAt);
        return pageWithNames(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public Page<Application> getMyApprovedApplications(Long approverId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Application> queryWrapper = new LambdaQueryWrapper<Application>()
                .eq(Application::getApproverId, approverId);
        if (status != null) {
            queryWrapper.eq(Application::getStatus, status);
        } else {
            // 默认查询已通过和已驳回的
            queryWrapper.in(Application::getStatus,
                    ApplicationStatusEnum.APPROVED.getCode(), ApplicationStatusEnum.REJECTED.getCode());
        }
        queryWrapper.orderByDesc(Application::getApprovedAt);
        return pageWithNames(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public Application getApplicationDetail(Long applicationId) {
        Application application = this.getById(applicationId);
        if (application != null) {
            fillUserNames(List.of(application));
        }
        return application;
    }

    @Override
    public void fillUserNames(List<Application> applications) {
        if (applications == null || applications.isEmpty()) {
            return;
        }
        Collection<Long> userIds = applications.stream()
                .flatMap(app -> Stream.of(app.getApplicantId(), app.getApproverId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<User> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<Long, String> names = users.stream()
                .collect(Collectors.toMap(User::getId, this::displayName, (a, b) -> a));
        Function<Long, String> nameOf = id -> id == null ? null : names.get(id);
        applications.forEach(app -> {
            app.setApplicantName(nameOf.apply(app.getApplicantId()));
            app.setApproverName(nameOf.apply(app.getApproverId()));
        });
    }

    private Page<Application> pageWithNames(Page<Application> page, LambdaQueryWrapper<Application> queryWrapper) {
        Page<Application> result = this.page(page, queryWrapper);
        fillUserNames(result.getRecords());
        return result;
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private Application requireApplication(Long applicationId) {
        Application application = this.getById(applicationId);
        if (application == null) {
            throw BusinessException.notFound("申请单不存在");
        }
        return application;
    }

    private boolean isParticipant(Application application, Long userId) {
        return application.getApplicantId().equals(userId) || application.getApproverId().equals(userId);
    }

    private void assertParticipant(Application application, Long userId) {
        if (!isParticipant(application, userId)) {
            throw BusinessException.forbidden("无权访问此申请");
        }
    }

    private void assertAssignedApprover(Application application, Long approverId) {
        if (!application.getApproverId().equals(approverId)) {
            throw BusinessException.forbidden("没有权限审批此申请");
        }
    }
}
