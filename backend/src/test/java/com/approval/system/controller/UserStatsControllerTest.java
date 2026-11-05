package com.approval.system.controller;

import com.approval.system.service.IApplicationService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.support.MockMvcSupport;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static com.approval.system.support.MockMvcSupport.loginAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserStatsControllerTest {

    @Mock
    private IApplicationService applicationService;

    @Mock
    private IUserRelationService userRelationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserStatsController controller = new UserStatsController();
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        ReflectionTestUtils.setField(controller, "userRelationService", userRelationService);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsPersonalCountersOnTheLegacyAdminUrl() throws Exception {
        loginAs(2L);
        when(applicationService.count(any(Wrapper.class))).thenReturn(3L);
        when(userRelationService.count(any(Wrapper.class))).thenReturn(1L);

        mockMvc.perform(get("/api/admin/user/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.myPending").value(3))
                .andExpect(jsonPath("$.data.myApproved").value(3))
                .andExpect(jsonPath("$.data.myRejected").value(3))
                .andExpect(jsonPath("$.data.pendingForMe").value(3))
                .andExpect(jsonPath("$.data.myRelations").value(1));
    }
}
