package com.approval.system.service.notification;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.NotifyTypeEnum;
import com.approval.system.entity.Application;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IApprovalAttachmentService;
import com.approval.system.service.IEmailService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IVoiceNotificationService;
import com.approval.system.service.impl.ApplicationServiceImpl;
import com.approval.system.service.impl.VoiceApplicationProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 通知必须在事务提交之后才发出：用真实的 Spring 事务基础设施（@Transactional 代理 +
 * @TransactionalEventListener）和一个记录提交时刻的事务管理器来验证。
 * 测试上下文里没有 @EnableAsync，所以监听器在提交后同步执行，便于断言；
 * 生产环境的异步由 {@link #listenersRunAsynchronouslyAfterCommit()} 守护。
 */
@SpringJUnitConfig(ApplicationNotificationAfterCommitTest.Config.class)
class ApplicationNotificationAfterCommitTest {

    private static final long ADMIN_ID = 1L;
    private static final long APPLICANT_ID = 2L;
    private static final long APPROVER_ID = 3L;

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        ApplicationNotifier applicationNotifier() {
            return new ApplicationNotifier();
        }

        @Bean
        ApplicationServiceImpl applicationService() {
            return new ApplicationServiceImpl();
        }
    }

    /**
     * 不连数据库的事务管理器，只记录"是否已经提交"
     */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        volatile boolean committed;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            committed = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            committed = false;
        }
    }

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private RecordingTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private ApplicationMapper applicationMapper;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private IOperationLogService operationLogService;

    @MockitoBean
    private IApprovalAttachmentService approvalAttachmentService;

    @MockitoBean
    private VoiceApplicationProcessor voiceApplicationProcessor;

    @MockitoBean
    private IEmailService emailService;

    @MockitoBean
    private INotificationService notificationService;

    @MockitoBean
    private IVoiceNotificationService voiceNotificationService;

    @Test
    void adminApprovalEmailsTheApplicantOnlyAfterCommit() {
        givenPendingApplication();
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant());
        when(userMapper.selectById(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).username("admin").realName("管理员").build());
        AtomicBoolean committedWhenSent = new AtomicBoolean(false);
        when(emailService.sendApprovalNotification(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    committedWhenSent.set(transactionManager.committed);
                    return true;
                });

        applicationService.adminApproveApplication(10L, ADMIN_ID, "管理员批准");

        verify(emailService).sendApprovalNotification("zhouyu@example.com", "周屿", "管理员", "周末加班", "管理员批准", 10L);
        assertThat(committedWhenSent).as("邮件发送时事务应已提交").isTrue();
        verify(notificationService).createSentNotification(10L, APPLICANT_ID, NotifyTypeEnum.EMAIL.getCode(),
                "申请已批准 - 周末加班", "您提交的申请已被批准", null, "zhouyu@example.com", true);
    }

    @Test
    void adminRejectionEmailsTheApplicantAfterCommit() {
        givenPendingApplication();
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant());
        when(emailService.sendRejectionNotification(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        applicationService.adminRejectApplication(10L, ADMIN_ID, "信息不完整");

        // 管理员账号查不到时沿用默认称呼"审批人"
        verify(emailService).sendRejectionNotification("zhouyu@example.com", "周屿", "审批人", "周末加班", "信息不完整", 10L);
        verify(notificationService).createSentNotification(10L, APPLICANT_ID, NotifyTypeEnum.EMAIL.getCode(),
                "申请已驳回 - 周末加班", "您提交的申请已被驳回", null, "zhouyu@example.com", false);
    }

    @Test
    void createApplicationCallsAndEmailsTheApproverAfterCommit() {
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            invocation.<Application>getArgument(0).setId(55L);
            return 1;
        });
        User applicant = applicant();
        applicant.setVoiceNotificationEnabled(true);
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant);
        when(userMapper.selectById(APPROVER_ID)).thenReturn(approver());
        AtomicBoolean committedWhenCalled = new AtomicBoolean(false);
        when(voiceNotificationService.notifyApproverNewApplication("13800000003", "周屿", "周末加班"))
                .thenAnswer(invocation -> {
                    committedWhenCalled.set(transactionManager.committed);
                    return true;
                });
        when(emailService.sendApplicationNotification("linlan@example.com", "周屿", "周末加班", 55L)).thenReturn(true);

        applicationService.createApplication(APPLICANT_ID, APPROVER_ID, "周末加班", "说明", null, true);

        assertThat(committedWhenCalled).as("语音通知时事务应已提交").isTrue();
        String content = "您有来自 周屿 的待审批申请: 周末加班";
        verify(notificationService).createSentNotification(55L, APPROVER_ID, NotifyTypeEnum.SMS.getCode(),
                "新的待审批申请", content, "13800000003", null, true);
        verify(notificationService).createSentNotification(55L, APPROVER_ID, NotifyTypeEnum.EMAIL.getCode(),
                "新的待审批申请", content, null, "linlan@example.com", true);
    }

    @Test
    void voiceCallIsSkippedSilentlyWithoutPermission() {
        when(applicationMapper.insert(any(Application.class))).thenReturn(1);
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant());
        when(userMapper.selectById(APPROVER_ID)).thenReturn(approver());

        applicationService.createApplication(APPLICANT_ID, APPROVER_ID, "周末加班", "说明", null, true);

        verifyNoInteractions(voiceNotificationService);
        verify(emailService).sendApplicationNotification("linlan@example.com", "周屿", "周末加班", null);
    }

    @Test
    void failingMailServerDoesNotFailTheRequest() {
        when(applicationMapper.insert(any(Application.class))).thenReturn(1);
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant());
        when(userMapper.selectById(APPROVER_ID)).thenReturn(approver());
        when(emailService.sendApplicationNotification(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("SMTP timeout"));

        assertThatCode(() -> applicationService.createApplication(APPLICANT_ID, APPROVER_ID, "周末加班", "说明", null, false))
                .doesNotThrowAnyException();
        verifyNoInteractions(notificationService);
    }

    @Test
    void nothingIsSentWhenTheTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new ApplicationDecidedEvent(10L, APPLICANT_ID, ADMIN_ID, "周末加班", true, "批准"));
            status.setRollbackOnly();
        });

        verifyNoInteractions(emailService, notificationService, userMapper);
    }

    @Test
    void eventsPublishedOutsideATransactionAreStillDelivered() {
        // 语音申请在异步线程里完成上传，那里没有事务
        when(userMapper.selectById(APPLICANT_ID)).thenReturn(applicant());
        when(userMapper.selectById(APPROVER_ID)).thenReturn(approver());

        eventPublisher.publishEvent(new ApplicationSubmittedEvent(99L, APPLICANT_ID, APPROVER_ID, "语音申请", false));

        verify(emailService).sendApplicationNotification("linlan@example.com", "周屿", "语音申请", 99L);
    }

    @Test
    void listenersRunAsynchronouslyAfterCommit() throws NoSuchMethodException {
        for (Method method : new Method[]{
                ApplicationNotifier.class.getMethod("onApplicationSubmitted", ApplicationSubmittedEvent.class),
                ApplicationNotifier.class.getMethod("onApplicationDecided", ApplicationDecidedEvent.class)}) {
            assertThat(method.isAnnotationPresent(Async.class)).as(method.getName() + " 应为 @Async").isTrue();
            TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
            assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(listener.fallbackExecution()).isTrue();
        }
    }

    private void givenPendingApplication() {
        when(applicationMapper.selectById(10L)).thenReturn(Application.builder()
                .id(10L)
                .applicantId(APPLICANT_ID)
                .approverId(APPROVER_ID)
                .title("周末加班")
                .status(ApplicationStatusEnum.PENDING.getCode())
                .build());
    }

    private static User applicant() {
        return User.builder().id(APPLICANT_ID).username("zhouyu").realName("周屿").email("zhouyu@example.com").build();
    }

    private static User approver() {
        return User.builder().id(APPROVER_ID).username("linlan").realName("林岚")
                .phone("13800000003").email("linlan@example.com").build();
    }
}
