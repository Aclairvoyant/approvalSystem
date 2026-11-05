package com.approval.system.service.impl;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.service.notification.ApplicationNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 手动语音通知会给审批人打电话，只有申请人本人能触发。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationVoiceNotificationAccessTest {

    private static final long APPLICANT = 10L;
    private static final long APPROVER = 20L;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private ApplicationNotifier applicationNotifier;

    private ApplicationServiceImpl service;

    private final Application application = Application.builder()
            .id(99L)
            .applicantId(APPLICANT)
            .approverId(APPROVER)
            .status(ApplicationStatusEnum.PENDING.getCode())
            .title("周末出去玩")
            .build();

    @BeforeEach
    void setUp() {
        service = new ApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        ReflectionTestUtils.setField(service, "applicationNotifier", applicationNotifier);
    }

    @Test
    void applicantCanCallTheApprover() {
        when(applicationMapper.selectById(99L)).thenReturn(application);
        when(applicationNotifier.sendVoiceNotificationToApprover(application, APPLICANT)).thenReturn(true);

        assertThat(service.sendVoiceNotificationToApprover(99L, APPLICANT)).isTrue();
        verify(applicationNotifier).sendVoiceNotificationToApprover(application, APPLICANT);
    }

    @Test
    void approverCannotTriggerTheCall() {
        when(applicationMapper.selectById(99L)).thenReturn(application);

        assertForbidden(APPROVER);
    }

    @Test
    void outsiderCannotTriggerTheCall() {
        when(applicationMapper.selectById(99L)).thenReturn(application);

        assertForbidden(30L);
    }

    @Test
    void missingApplicationStillReturnsFalse() {
        when(applicationMapper.selectById(99L)).thenReturn(null);

        assertThat(service.sendVoiceNotificationToApprover(99L, APPLICANT)).isFalse();
        verifyNoInteractions(applicationNotifier);
    }

    private void assertForbidden(Long operatorId) {
        assertThatThrownBy(() -> service.sendVoiceNotificationToApprover(99L, operatorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作此申请")
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
        verifyNoInteractions(applicationNotifier);
    }
}
