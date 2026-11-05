package com.approval.system.controller;

import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.entity.ApprovalAttachment;
import com.approval.system.service.IApplicationAttachmentService;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IApprovalAttachmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.approval.system.support.MockMvcSupport;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerSecurityTest {

    @Mock
    private IApplicationAttachmentService applicationAttachmentService;

    @Mock
    private IApprovalAttachmentService approvalAttachmentService;

    @Mock
    private IApplicationService applicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AttachmentController controller = new AttachmentController();
        ReflectionTestUtils.setField(controller, "applicationAttachmentService", applicationAttachmentService);
        ReflectionTestUtils.setField(controller, "approvalAttachmentService", approvalAttachmentService);
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getApplicationAttachmentsRequiresParticipantAccess() throws Exception {
        setCurrentUser(10L);
        when(applicationAttachmentService.getApplicationAttachments(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/attachments/application/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).assertApplicationParticipant(99L, 10L);
    }

    @Test
    void uploadApplicationAttachmentRequiresApplicantAccess() throws Exception {
        setCurrentUser(10L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", new byte[]{1, 2, 3, 4}
        );
        when(applicationAttachmentService.uploadApplicationAttachment(99L, file))
                .thenReturn(ApplicationAttachment.builder()
                        .id(7L)
                        .applicationId(99L)
                        .fileName("voice.wav")
                        .fileUrl("https://example.test/voice.wav")
                        .fileType("audio/wav")
                        .fileSize(4L)
                        .build());

        mockMvc.perform(multipart("/api/attachments/application/99").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).assertApplicationApplicant(99L, 10L);
    }

    @Test
    void uploadApprovalAttachmentRequiresApproverAccess() throws Exception {
        setCurrentUser(20L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "reply.pdf", "application/pdf", new byte[]{1, 2, 3, 4}
        );
        when(approvalAttachmentService.uploadApprovalAttachment(99L, 20L, file))
                .thenReturn(ApprovalAttachment.builder()
                        .id(8L)
                        .applicationId(99L)
                        .operatorId(20L)
                        .fileName("reply.pdf")
                        .fileUrl("https://example.test/reply.pdf")
                        .fileType("application/pdf")
                        .fileSize(4L)
                        .build());

        mockMvc.perform(multipart("/api/attachments/approval/99").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(applicationService).assertApplicationApprover(99L, 20L);
    }

    private void setCurrentUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
