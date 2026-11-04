package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.utils.JwtUtils;
import com.approval.system.common.utils.OssUtils;
import com.approval.system.entity.Application;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IEmailService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IUserService;
import com.approval.system.service.IVoiceApplicationService;
import com.approval.system.support.MockMvcSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static com.approval.system.support.MockMvcSupport.loginAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Valid 已接到控制器上：非法请求在进入 service 之前就被拦下。
 */
@ExtendWith(MockitoExtension.class)
class RequestValidationControllerTest {

    @Mock
    private IApplicationService applicationService;

    @Mock
    private IUserRelationService userRelationService;

    @Mock
    private IUserService userService;

    @Mock
    private IEmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationController applicationController = new ApplicationController();
        ReflectionTestUtils.setField(applicationController, "applicationService", applicationService);
        ReflectionTestUtils.setField(applicationController, "userRelationService", userRelationService);
        ReflectionTestUtils.setField(applicationController, "operationLogService", mock(IOperationLogService.class));
        ReflectionTestUtils.setField(applicationController, "voiceApplicationService", mock(IVoiceApplicationService.class));

        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "userService", userService);
        ReflectionTestUtils.setField(authController, "jwtUtils", mock(JwtUtils.class));
        ReflectionTestUtils.setField(authController, "ossUtils", mock(OssUtils.class));
        ReflectionTestUtils.setField(authController, "emailService", emailService);

        mockMvc = MockMvcSupport.standalone(applicationController, authController);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createApplicationWithBlankTitleNeverReachesTheService() throws Exception {
        loginAs(10L);

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approverId\":20,\"title\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入标题"));

        verifyNoInteractions(userRelationService, applicationService);
    }

    @Test
    void updateApplicationDoesNotNeedApproverId() throws Exception {
        loginAs(10L);
        when(applicationService.updateApplication(99L, 10L, "新标题", "说明", null))
                .thenReturn(Application.builder().id(99L).title("新标题").build());

        mockMvc.perform(put("/api/applications/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"description\":\"说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    @Test
    void overlongApprovalDetailIsRejected() throws Exception {
        loginAs(20L);

        mockMvc.perform(post("/api/applications/99/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalDetail\":\"" + "意".repeat(501) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("审批意见不能超过500个字符"));

        verify(applicationService, never()).approveApplication(anyLong(), anyLong(), any());
    }

    @Test
    void registerRejectsMalformedPhoneBeforeTouchingUsers() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhouyu\",\"phone\":\"123\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入正确的手机号"));

        verifyNoInteractions(userService, emailService);
    }

    @Test
    void changePasswordRequiresMinimumLength() throws Exception {
        loginAs(10L);

        mockMvc.perform(put("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"secret1\",\"newPassword\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码长度需为6-64位"));

        verifyNoInteractions(userService);
    }

    @Test
    void loginFailureMessageFromServiceStillReachesTheUser() throws Exception {
        when(userService.login("zhouyu", "wrong"))
                .thenThrow(BusinessException.badRequest("密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhouyu\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码错误"));
    }
}
