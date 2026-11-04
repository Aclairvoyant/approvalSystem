package com.approval.system.service.impl;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.User;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationParticipantAccessTest {

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private UserMapper userMapper;

    private ApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .build());
    }

    @Test
    void applicantAndApproverCanAccessWithoutUserLookup() {
        assertThatCode(() -> service.assertApplicationParticipantOrAdmin(99L, 10L)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertApplicationParticipantOrAdmin(99L, 20L)).doesNotThrowAnyException();
        verifyNoInteractions(userMapper);
    }

    @Test
    void adminCanAccessOtherUsersApplication() {
        when(userMapper.selectById(1L)).thenReturn(User.builder().id(1L).role(1).build());

        assertThatCode(() -> service.assertApplicationParticipantOrAdmin(99L, 1L)).doesNotThrowAnyException();
    }

    @Test
    void unrelatedNonAdminIsRejected() {
        when(userMapper.selectById(30L)).thenReturn(User.builder().id(30L).role(0).build());

        assertThatThrownBy(() -> service.assertApplicationParticipantOrAdmin(99L, 30L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问此申请")
                .extracting("code").isEqualTo(BusinessException.FORBIDDEN);
    }
}
