package com.approval.system.controller;

import com.approval.system.entity.Application;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IVoiceApplicationService;
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
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerVoiceTest {

    @Mock
    private IApplicationService applicationService;

    @Mock
    private IUserRelationService userRelationService;

    @Mock
    private IOperationLogService operationLogService;

    @Mock
    private IVoiceApplicationService voiceApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApplicationController controller = new ApplicationController();
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        ReflectionTestUtils.setField(controller, "userRelationService", userRelationService);
        ReflectionTestUtils.setField(controller, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(controller, "voiceApplicationService", voiceApplicationService);
        mockMvc = MockMvcSupport.standalone(controller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createVoiceApplicationAcceptsMultipartAudioWithoutTitle() throws Exception {
        setCurrentUser(10L);
        MockMultipartFile audio = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", new byte[]{1, 2, 3, 4}
        );
        Application saved = Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .title("语音申请")
                .description("语音申请，请播放语音内容")
                .appType(2)
                .status(1)
                .build();

        when(userRelationService.isRelated(10L, 20L)).thenReturn(true);
        when(applicationService.createVoiceApplicationDraft(10L, 20L)).thenReturn(saved);

        mockMvc.perform(multipart("/api/applications/voice")
                        .file(audio)
                        .param("approverId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.appType").value(2))
                .andExpect(jsonPath("$.data.title").value("语音申请"));

        verify(applicationService).validateVoiceApplicationAudioUpload(eq(99L), eq(10L), any(MultipartFile.class));
        verify(applicationService).processVoiceApplicationAudioAsync(
                eq(99L), eq(10L), any(byte[].class), eq("voice.wav"), eq("audio/wav"), eq(4L));
    }

    @Test
    void createVoiceApplicationRejectsUnrelatedApprover() throws Exception {
        setCurrentUser(10L);
        MockMultipartFile audio = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", new byte[]{1, 2, 3, 4}
        );
        when(userRelationService.isRelated(10L, 20L)).thenReturn(false);

        mockMvc.perform(multipart("/api/applications/voice")
                        .file(audio)
                        .param("approverId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有互为对象的用户才能创建申请"));

        verify(applicationService, never()).createVoiceApplicationDraft(anyLong(), anyLong());
    }

    @Test
    void transcribeVoiceApplicationReturnsTranscriptForParticipant() throws Exception {
        setCurrentUser(20L);
        when(applicationService.transcribeVoiceApplication(99L, 20L, "zh"))
                .thenReturn("明天想请假一天");

        mockMvc.perform(post("/api/applications/99/transcribe-voice")
                        .param("language", "zh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("明天想请假一天"));
    }

    @Test
    void getApplicationDetailRequiresParticipantAccess() throws Exception {
        setCurrentUser(30L);
        when(applicationService.getApplicationDetail(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .title("语音申请")
                .build());

        mockMvc.perform(get("/api/applications/99"))
                .andExpect(status().isOk());

        verify(applicationService).assertApplicationParticipant(99L, 30L);
    }

    private void setCurrentUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }
}
