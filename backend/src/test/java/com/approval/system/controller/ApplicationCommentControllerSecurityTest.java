package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.service.IApplicationCommentService;
import com.approval.system.service.IApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.approval.system.support.MockMvcSupport;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApplicationCommentControllerSecurityTest {

    @Mock
    private IApplicationCommentService commentService;

    @Mock
    private IApplicationService applicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationCommentController controller = new ApplicationCommentController();
        ReflectionTestUtils.setField(controller, "commentService", commentService);
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCommentsRequiresParticipantOrAdmin() throws Exception {
        setCurrentUser(10L);
        when(commentService.getApplicationComments(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/comments/application/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).assertApplicationParticipantOrAdmin(99L, 10L);
    }

    @Test
    void getCommentsRejectsOutsider() throws Exception {
        setCurrentUser(30L);
        doThrow(BusinessException.forbidden("无权访问此申请"))
                .when(applicationService).assertApplicationParticipantOrAdmin(99L, 30L);

        mockMvc.perform(get("/api/comments/application/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问此申请"));

        verifyNoInteractions(commentService);
    }

    @Test
    void createCommentRejectsOutsider() throws Exception {
        setCurrentUser(30L);
        doThrow(BusinessException.forbidden("无权访问此申请"))
                .when(applicationService).assertApplicationParticipantOrAdmin(99L, 30L);

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":99,\"content\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问此申请"));

        verify(commentService, never()).createComment(anyLong(), anyLong(), any(), any());
    }

    private void setCurrentUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
