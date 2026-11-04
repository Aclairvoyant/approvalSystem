package com.approval.system.service.notification;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.NotifyTypeEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IEmailService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.IVoiceNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 申请相关的邮件 / 语音通知，以及对应的 notifications 记录。
 * <p>
 * 事件监听方法在事务提交之后、在异步线程里执行：SMTP 和语音接口很慢且可能失败，
 * 不能占着数据库事务，也不能因为通知失败让审批操作回滚；事务回滚时则根本不会发通知。
 * 没有事务时（例如语音申请的异步处理线程）{@code fallbackExecution = true} 让监听器直接执行。
 * 通知失败只记日志和 notifications 记录，不向调用方抛异常。
 */
@Slf4j
@Component
public class ApplicationNotifier {

    static final String NEW_APPLICATION_TITLE = "新的待审批申请";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IEmailService emailService;

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private IVoiceNotificationService voiceNotificationService;

    /**
     * 新的待审批申请：按需给审批人打语音电话，并发送邮件。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        if (event.sendVoiceNotification()) {
            try {
                sendVoiceOnSubmit(event);
            } catch (Exception e) {
                log.error("发送语音通知失败，但申请创建成功，applicationId: {}", event.applicationId(), e);
            }
        }
        try {
            sendSubmittedEmail(event);
        } catch (Exception e) {
            log.error("发送邮件通知失败，但申请创建成功，applicationId: {}", event.applicationId(), e);
        }
    }

    /**
     * 申请被批准 / 驳回：邮件通知申请人。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApplicationDecided(ApplicationDecidedEvent event) {
        String action = event.approved() ? "批准" : "驳回";
        try {
            sendDecisionEmail(event);
        } catch (Exception e) {
            log.error("发送{}通知失败，但审批操作已完成，applicationId: {}", action, event.applicationId(), e);
        }
    }

    /**
     * 申请人手动触发的语音通知（同步执行，调用方需要知道是否成功）。
     *
     * @return 语音接口是否调用成功；申请不是待审批、审批人没有手机号或接口失败时返回 false
     * @throws BusinessException 操作人不存在或未开通语音通知权限
     */
    public boolean sendVoiceNotificationToApprover(Application application, Long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw BusinessException.notFound("操作者不存在");
        }
        if (!Boolean.TRUE.equals(operator.getVoiceNotificationEnabled())) {
            log.warn("用户未开通语音通知权限，userId: {}", operatorId);
            throw BusinessException.forbidden("您未开通语音通知权限，请联系管理员开通");
        }

        try {
            // 只有待审批状态才能发送语音通知
            if (!ApplicationStatusEnum.PENDING.is(application.getStatus())) {
                log.warn("只有待审批状态的申请才能发送语音通知，当前状态: {}", application.getStatus());
                return false;
            }

            User applicant = userMapper.selectById(application.getApplicantId());
            User approver = userMapper.selectById(application.getApproverId());
            if (approver == null || !StringUtils.hasLength(approver.getPhone())) {
                log.warn("审批人手机号为空，无法发送语音通知，审批人ID: {}", application.getApproverId());
                return false;
            }

            String applicantName = displayName(applicant, "用户");
            boolean success = callApprover(application.getId(), application.getApproverId(), approver.getPhone(),
                    applicantName, application.getTitle());
            if (success) {
                log.info("手动触发语音通知成功，applicationId: {}, 手机号: {}, 操作人: {}",
                        application.getId(), approver.getPhone(), operatorId);
            } else {
                log.warn("手动触发语音通知失败，applicationId: {}, 手机号: {}", application.getId(), approver.getPhone());
            }
            return success;
        } catch (Exception e) {
            log.error("手动触发语音通知异常，applicationId: {}", application.getId(), e);
            return false;
        }
    }

    private void sendVoiceOnSubmit(ApplicationSubmittedEvent event) {
        // 检查申请人是否有语音通知权限；没有权限时静默跳过，不影响申请
        User applicant = userMapper.selectById(event.applicantId());
        if (applicant == null) {
            log.error("申请人不存在，applicantId: {}", event.applicantId());
            return;
        }
        if (!Boolean.TRUE.equals(applicant.getVoiceNotificationEnabled())) {
            log.warn("用户未开通语音通知权限，userId: {}", event.applicantId());
            return;
        }

        User approver = userMapper.selectById(event.approverId());
        if (approver == null) {
            return;
        }
        if (!StringUtils.hasLength(approver.getPhone())) {
            log.warn("审批人手机号为空，无法发送语音通知，审批人ID: {}", event.approverId());
            return;
        }

        boolean success = callApprover(event.applicationId(), event.approverId(), approver.getPhone(),
                displayName(applicant, "用户"), event.title());
        if (success) {
            log.info("语音通知发送成功，手机号: {}", approver.getPhone());
        } else {
            log.warn("语音通知发送失败，手机号: {}", approver.getPhone());
        }
    }

    private boolean callApprover(Long applicationId, Long approverId, String phone, String applicantName, String title) {
        boolean success = voiceNotificationService.notifyApproverNewApplication(phone, applicantName, title);
        notificationService.createSentNotification(
                applicationId,
                approverId,
                NotifyTypeEnum.SMS.getCode(), // 语音通知按短信类记录
                NEW_APPLICATION_TITLE,
                newApplicationContent(applicantName, title),
                phone,
                null,
                success
        );
        return success;
    }

    private void sendSubmittedEmail(ApplicationSubmittedEvent event) {
        User approver = userMapper.selectById(event.approverId());
        if (approver == null) {
            return;
        }
        if (!StringUtils.hasText(approver.getEmail())) {
            log.warn("审批人邮箱为空，无法发送邮件通知，审批人ID: {}", event.approverId());
            return;
        }

        String applicantName = displayName(userMapper.selectById(event.applicantId()), "用户");
        boolean success = emailService.sendApplicationNotification(
                approver.getEmail(), applicantName, event.title(), event.applicationId());
        notificationService.createSentNotification(
                event.applicationId(),
                event.approverId(),
                NotifyTypeEnum.EMAIL.getCode(),
                NEW_APPLICATION_TITLE,
                newApplicationContent(applicantName, event.title()),
                null,
                approver.getEmail(),
                success
        );
        if (success) {
            log.info("邮件通知发送成功，邮箱: {}", approver.getEmail());
        } else {
            log.warn("邮件通知发送失败，邮箱: {}", approver.getEmail());
        }
    }

    private void sendDecisionEmail(ApplicationDecidedEvent event) {
        String action = event.approved() ? "批准" : "驳回";
        User applicant = userMapper.selectById(event.applicantId());
        if (applicant == null || !StringUtils.hasLength(applicant.getEmail())) {
            log.warn("申请人邮箱为空，无法发送{}通知，申请人ID: {}", action, event.applicantId());
            return;
        }

        String applicantName = displayName(applicant, "用户");
        String approverName = displayName(userMapper.selectById(event.operatorId()), "审批人");
        boolean success = event.approved()
                ? emailService.sendApprovalNotification(applicant.getEmail(), applicantName, approverName,
                        event.title(), event.detail(), event.applicationId())
                : emailService.sendRejectionNotification(applicant.getEmail(), applicantName, approverName,
                        event.title(), event.detail(), event.applicationId());

        notificationService.createSentNotification(
                event.applicationId(),
                event.applicantId(),
                NotifyTypeEnum.EMAIL.getCode(),
                "申请已" + action + " - " + event.title(),
                "您提交的申请已被" + action,
                null,
                applicant.getEmail(),
                success
        );
        if (success) {
            log.info("{}通知邮件发送成功，applicationId: {}, 邮箱: {}", action, event.applicationId(), applicant.getEmail());
        } else {
            log.warn("{}通知邮件发送失败，applicationId: {}, 邮箱: {}", action, event.applicationId(), applicant.getEmail());
        }
    }

    private static String newApplicationContent(String applicantName, String title) {
        return "您有来自 " + applicantName + " 的待审批申请: " + title;
    }

    /**
     * 与改造前一致：有 realName（哪怕是空串）就用 realName，否则用 username；用户不存在时用默认称呼。
     */
    private static String displayName(User user, String fallback) {
        if (user == null) {
            return fallback;
        }
        return user.getRealName() != null ? user.getRealName() : user.getUsername();
    }
}
