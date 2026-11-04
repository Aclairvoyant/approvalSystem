package com.approval.system.controller;

import com.approval.system.common.security.ActiveUserChecker;
import com.approval.system.dto.VoiceModelSettingsResponse;
import com.approval.system.entity.User;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.ISystemSettingService;
import com.approval.system.service.IUserService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminController 的业务行为。谁能访问 /api/admin/** 由 Spring Security 决定，
 * 见 {@link AdminSecurityWebMvcTest}；这里的 standalone MockMvc 不经过安全过滤器。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private IUserService userService;

    @Mock
    private IApplicationService applicationService;

    @Mock
    private INotificationService notificationService;

    @Mock
    private ISystemSettingService systemSettingService;

    @Mock
    private ActiveUserChecker activeUserChecker;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        ReflectionTestUtils.setField(controller, "systemSettingService", systemSettingService);
        ReflectionTestUtils.setField(controller, "activeUserChecker", activeUserChecker);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMimoConfigReturnsMaskedVoiceSettings() throws Exception {
        loginAs(1L);
        when(systemSettingService.getVoiceModelSettings()).thenReturn(VoiceModelSettingsResponse.builder()
                .enabled(true)
                .asrBaseUrl("https://token-plan-cn.xiaomimimo.com/v1")
                .asrModel("mimo-v2.5-asr")
                .chatModel("mimo-v2.5")
                .asrApiKeyConfigured(true)
                .asrApiKeyMasked("tp-c...wbh")
                .build());

        mockMvc.perform(get("/api/admin/mimo/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.baseUrl").value("https://token-plan-cn.xiaomimimo.com/v1"))
                .andExpect(jsonPath("$.data.asrModel").value("mimo-v2.5-asr"))
                .andExpect(jsonPath("$.data.chatModel").value("mimo-v2.5"))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("tp-c...wbh"));
    }

    @Test
    void updateUserStatusRejectsDisablingOwnAccount() throws Exception {
        loginAs(1L);

        mockMvc.perform(put("/api/admin/users/1/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能修改自己的账号状态"));

        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateUserRoleRejectsDemotingOwnAccount() throws Exception {
        loginAs(1L);

        mockMvc.perform(put("/api/admin/users/1/role").param("role", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能修改自己的角色"));

        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void updateUserStatusDisablesOtherUserAndDropsCachedStatus() throws Exception {
        loginAs(1L);
        when(userService.getById(2L)).thenReturn(User.builder().id(2L).role(0).status(1).password("hash").build());

        mockMvc.perform(put("/api/admin/users/2/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(content().string(not(containsString("hash"))));

        verify(userService).updateById(any(User.class));
        verify(activeUserChecker).evict(2L);
    }

    @Test
    void updateUserRoleDropsCachedAuthState() throws Exception {
        loginAs(1L);
        when(userService.getById(2L)).thenReturn(User.builder().id(2L).role(1).status(1).build());

        mockMvc.perform(put("/api/admin/users/2/role").param("role", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.role").value(0));

        verify(activeUserChecker).evict(2L);
    }

    @Test
    void updateUserStatusRejectsUnknownStatusValue() throws Exception {
        loginAs(1L);

        mockMvc.perform(put("/api/admin/users/2/status").param("status", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户状态只能是 0 或 1"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateUserStatusReturns404ForMissingUser() throws Exception {
        loginAs(1L);

        mockMvc.perform(put("/api/admin/users/9/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void updateUserInfoRejectsMalformedPhone() throws Exception {
        loginAs(1L);

        mockMvc.perform(put("/api/admin/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"林岚\",\"phone\":\"12345\",\"email\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("手机号格式不正确"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateUserInfoRejectsPhoneOwnedByAnotherUser() throws Exception {
        loginAs(1L);
        when(userService.getById(2L)).thenReturn(User.builder().id(2L).phone("13800000002").build());
        when(userService.getUserByPhone("13800000003")).thenReturn(User.builder().id(3L).build());

        mockMvc.perform(put("/api/admin/users/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000003\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该手机号已被其他用户使用"));

        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void adminApproveDelegatesWithDefaultDetail() throws Exception {
        loginAs(1L);

        mockMvc.perform(post("/api/admin/applications/10/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).adminApproveApplication(10L, 1L, "管理员批准");
    }

    @Test
    void adminRejectRejectsOverlongReason() throws Exception {
        loginAs(1L);

        mockMvc.perform(post("/api/admin/applications/10/reject").param("rejectReason", "长".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("驳回原因不能超过500个字符"));

        verify(applicationService, never()).adminRejectApplication(anyLong(), anyLong(), any());
    }
}
