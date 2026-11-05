package com.approval.system.service.impl;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.OperationLog;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.notification.ApplicationDecidedEvent;
import com.approval.system.service.notification.ApplicationSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationAdminDecisionTest {

    private static final long APPLICANT_ID = 2L;
    private static final long APPROVER_ID = 3L;
    private static final long ADMIN_ID = 1L;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = ApplicationServiceTestSupport.applicationService(
                applicationMapper, userMapper, operationLogMapper, null, eventPublisher);
    }

    private Application pendingApplication() {
        return Application.builder()
                .id(10L)
                .applicantId(APPLICANT_ID)
                .approverId(APPROVER_ID)
                .title("周末加班")
                .status(ApplicationStatusEnum.PENDING.getCode())
                .build();
    }

    @Test
    void adminCanApproveApplicationAssignedToSomeoneElse() {
        Application application = pendingApplication();
        when(applicationMapper.selectById(10L)).thenReturn(application);

        service.adminApproveApplication(10L, ADMIN_ID, "管理员批准");

        ArgumentCaptor<Application> updated = ArgumentCaptor.forClass(Application.class);
        verify(applicationMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(ApplicationStatusEnum.APPROVED.getCode());

        ArgumentCaptor<OperationLog> logged = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logged.capture());
        assertThat(logged.getValue().getOperatorId()).isEqualTo(ADMIN_ID);
        assertThat(logged.getValue().getOperationType()).isEqualTo(OperationTypeEnum.APPROVE.getCode());
    }

    @Test
    void adminApprovePublishesDecisionEventSoTheApplicantIsNotified() {
        when(applicationMapper.selectById(10L)).thenReturn(pendingApplication());

        service.adminApproveApplication(10L, ADMIN_ID, "管理员批准");

        verify(eventPublisher).publishEvent(new ApplicationDecidedEvent(
                10L, APPLICANT_ID, ADMIN_ID, "周末加班", true, "管理员批准"));
    }

    @Test
    void adminRejectStoresReasonLogsAdminAndPublishesDecisionEvent() {
        when(applicationMapper.selectById(10L)).thenReturn(pendingApplication());

        service.adminRejectApplication(10L, ADMIN_ID, "信息不完整");

        ArgumentCaptor<Application> updated = ArgumentCaptor.forClass(Application.class);
        verify(applicationMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(ApplicationStatusEnum.REJECTED.getCode());
        assertThat(updated.getValue().getRejectReason()).isEqualTo("信息不完整");

        ArgumentCaptor<OperationLog> logged = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logged.capture());
        assertThat(logged.getValue().getOperatorId()).isEqualTo(ADMIN_ID);

        verify(eventPublisher).publishEvent(new ApplicationDecidedEvent(
                10L, APPLICANT_ID, ADMIN_ID, "周末加班", false, "信息不完整"));
    }

    @Test
    void adminCannotDecideApplicationThatIsNoLongerPending() {
        Application application = pendingApplication();
        application.setStatus(ApplicationStatusEnum.APPROVED.getCode());
        when(applicationMapper.selectById(10L)).thenReturn(application);

        assertThatThrownBy(() -> service.adminRejectApplication(10L, ADMIN_ID, "迟到的驳回"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有待处理的申请");
        verify(applicationMapper, never()).updateById(any(Application.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void regularApproveStillRequiresTheAssignedApprover() {
        when(applicationMapper.selectById(10L)).thenReturn(pendingApplication());

        assertThatThrownBy(() -> service.approveApplication(10L, ADMIN_ID, "不是我的申请"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有权限审批此申请")
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        verify(applicationMapper, never()).updateById(any(Application.class));
    }

    @Test
    void missingApplicationIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.adminApproveApplication(404L, ADMIN_ID, "批准"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申请单不存在")
                .extracting("code").isEqualTo(BusinessException.NOT_FOUND);
    }

    @Test
    void createApplicationPublishesSubmittedEventInsteadOfSendingMailInline() {
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            invocation.<Application>getArgument(0).setId(55L);
            return 1;
        });

        Application created = service.createApplication(APPLICANT_ID, APPROVER_ID, "周末加班", "说明", null, true);

        assertThat(created.getStatus()).isEqualTo(ApplicationStatusEnum.PENDING.getCode());
        verify(eventPublisher).publishEvent(new ApplicationSubmittedEvent(55L, APPLICANT_ID, APPROVER_ID, "周末加班", true));
        // 用户信息只在通知时（事务提交后）才查询
        verifyNoInteractions(userMapper);
    }

    @Test
    void fillUserNamesPrefersRealNameAndFallsBackToUsername() {
        Application application = pendingApplication();
        User applicant = new User();
        applicant.setId(APPLICANT_ID);
        applicant.setUsername("zhouyu");
        applicant.setRealName("周屿");
        User approver = new User();
        approver.setId(APPROVER_ID);
        approver.setUsername("linlan");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(applicant, approver));

        service.fillUserNames(List.of(application));

        assertThat(application.getApplicantName()).isEqualTo("周屿");
        assertThat(application.getApproverName()).isEqualTo("linlan");
    }
}
