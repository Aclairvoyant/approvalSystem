package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.approval.system.support.MockMvcSupport;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OperationLogControllerSecurityTest {

    @Mock
    private IOperationLogService operationLogService;

    @Mock
    private IUserService userService;

    @Mock
    private IApplicationService applicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OperationLogController controller = new OperationLogController();
        ReflectionTestUtils.setField(controller, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void timelineRequiresParticipantOrAdmin() throws Exception {
        setCurrentUser(1L);
        when(operationLogService.getApplicationTimeline(99L, 1, 100)).thenReturn(new Page<>(1, 100));

        mockMvc.perform(get("/api/operation-logs/timeline/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).assertApplicationParticipantOrAdmin(99L, 1L);
    }

    @Test
    void timelineRejectsOutsider() throws Exception {
        setCurrentUser(30L);
        doThrow(BusinessException.forbidden("无权访问此申请"))
                .when(applicationService).assertApplicationParticipantOrAdmin(99L, 30L);

        mockMvc.perform(get("/api/operation-logs/timeline/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问此申请"));

        verifyNoInteractions(operationLogService);
    }

    private void setCurrentUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
