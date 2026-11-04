package com.approval.system.controller;

import com.approval.system.common.config.SecurityConfig;
import com.approval.system.common.security.ActiveUserChecker;
import com.approval.system.common.security.JwtAuthenticationFilter;
import com.approval.system.common.utils.JwtUtils;
import com.approval.system.entity.User;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.ISystemSettingService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 走真实的 SecurityConfig + JwtAuthenticationFilter：角色来自 ActiveUserChecker（数据库），
 * /api/admin/** 需要 ROLE_ADMIN，/api/admin/user/stats 例外，对所有登录用户开放。
 */
@WebMvcTest(controllers = {AdminController.class, UserStatsController.class})
@Import(SecurityConfig.class)
class AdminSecurityWebMvcTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";
    private static final String DISABLED_TOKEN = "disabled-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminController adminController;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private ActiveUserChecker activeUserChecker;

    @MockitoBean
    private IUserService userService;

    @MockitoBean
    private IApplicationService applicationService;

    @MockitoBean
    private INotificationService notificationService;

    @MockitoBean
    private ISystemSettingService systemSettingService;

    @MockitoBean
    private IUserRelationService userRelationService;

    @BeforeEach
    void setUpTokens() {
        givenToken(ADMIN_TOKEN, 1L, new ActiveUserChecker.UserAuthState(true, true));
        givenToken(USER_TOKEN, 2L, new ActiveUserChecker.UserAuthState(true, false));
        givenToken(DISABLED_TOKEN, 3L, ActiveUserChecker.UserAuthState.INACTIVE);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestGets401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(applicationService);
    }

    @Test
    void regularUserIsForbiddenFromAdminEndpoints() throws Exception {
        mockMvc.perform(as(USER_TOKEN, get("/api/admin/dashboard/stats")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("没有权限访问该资源"));

        verifyNoInteractions(applicationService, userService);
    }

    @Test
    void regularUserCannotPromoteThemselvesOrOthers() throws Exception {
        mockMvc.perform(as(USER_TOKEN, put("/api/admin/users/2/role").param("role", "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(userService, never()).updateById(any(User.class));
    }

    @Test
    void regularUserCannotDecideApplicationsAsAdmin() throws Exception {
        mockMvc.perform(as(USER_TOKEN, post("/api/admin/applications/10/approve")))
                .andExpect(status().isForbidden());

        verify(applicationService, never()).adminApproveApplication(anyLong(), anyLong(), any());
    }

    @Test
    void adminCanReachAdminEndpoints() throws Exception {
        when(applicationService.count()).thenReturn(5L);

        mockMvc.perform(as(ADMIN_TOKEN, get("/api/admin/dashboard/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalApplications").value(5));
    }

    @Test
    void personalStatsStayAvailableToRegularUsers() throws Exception {
        when(applicationService.count(any(Wrapper.class))).thenReturn(0L);
        when(userRelationService.count(any(Wrapper.class))).thenReturn(1L);

        mockMvc.perform(as(USER_TOKEN, get("/api/admin/user/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.myRelations").value(1));
    }

    @Test
    void disabledAccountIsRejectedEvenWithAValidToken() throws Exception {
        mockMvc.perform(as(DISABLED_TOKEN, get("/api/admin/user/stats")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    void adminControllerIsGuardedByMethodSecurityToo() {
        // URL 规则之外的第二道保险：即使以后有人改了 SecurityConfig 的路径匹配，方法级校验仍会拒绝
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority(JwtAuthenticationFilter.ROLE_USER))));

        assertThatThrownBy(() -> adminController.getDashboardStats()).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(applicationService);
    }

    private void givenToken(String token, Long userId, ActiveUserChecker.UserAuthState state) {
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtUtils.getUsernameFromToken(token)).thenReturn("user" + userId);
        when(activeUserChecker.lookup(userId)).thenReturn(state);
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }
}
