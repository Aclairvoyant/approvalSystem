package com.approval.system.service.impl;

import com.approval.system.common.config.AliyunOssProperties;
import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.VoiceStatusEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IApplicationAttachmentService;
import com.approval.system.service.IVoiceApplicationService;
import com.approval.system.service.notification.ApplicationSubmittedEvent;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceVoiceTest {

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private IApplicationAttachmentService applicationAttachmentService;

    @Mock
    private IVoiceApplicationService voiceApplicationService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AliyunOssProperties ossProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        VoiceApplicationProcessor processor = new VoiceApplicationProcessor();
        ReflectionTestUtils.setField(processor, "applicationMapper", applicationMapper);
        ReflectionTestUtils.setField(processor, "operationLogService",
                ApplicationServiceTestSupport.operationLogService(operationLogMapper));
        ReflectionTestUtils.setField(processor, "applicationAttachmentService", applicationAttachmentService);
        ReflectionTestUtils.setField(processor, "voiceApplicationService", voiceApplicationService);
        ReflectionTestUtils.setField(processor, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(processor, "ossProperties", ossProperties);
        ReflectionTestUtils.setField(processor, "eventPublisher", eventPublisher);

        service = ApplicationServiceTestSupport.applicationService(
                applicationMapper, userMapper, operationLogMapper, processor, eventPublisher);
    }

    @Test
    void createVoiceApplicationGeneratesDisplayFieldsAndUploadsAudio() {
        MockMultipartFile audio = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", new byte[]{1, 2, 3, 4}
        );
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            application.setId(99L);
            return 1;
        });
        when(applicationAttachmentService.uploadApplicationAttachment(99L, audio))
                .thenReturn(ApplicationAttachment.builder()
                        .id(7L)
                        .applicationId(99L)
                        .fileType("audio/wav")
                        .build());

        Application result = service.createVoiceApplication(10L, 20L, audio);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getApplicantId()).isEqualTo(10L);
        assertThat(result.getApproverId()).isEqualTo(20L);
        assertThat(result.getAppType()).isEqualTo(2);
        assertThat(result.getTitle()).isEqualTo("语音申请");
        assertThat(result.getDescription()).isEqualTo("语音申请，请播放语音内容");
        verify(applicationAttachmentService).uploadApplicationAttachment(99L, audio);
    }

    @Test
    void createVoiceApplicationRejectsEmptyAudio() {
        MockMultipartFile audio = new MockMultipartFile(
                "file", "voice.wav", "audio/wav", new byte[0]
        );

        assertThatThrownBy(() -> service.createVoiceApplication(10L, 20L, audio))
                .isInstanceOf(BusinessException.class)
                .hasMessage("语音内容为空");
    }

    @Test
    void createVoiceApplicationRejectsNonWavAudio() {
        MockMultipartFile audio = new MockMultipartFile(
                "file", "voice.pdf", "application/pdf", new byte[]{1, 2, 3, 4}
        );

        assertThatThrownBy(() -> service.createVoiceApplication(10L, 20L, audio))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持 WAV 语音文件");
    }

    @Test
    void assertApplicationParticipantRejectsUnrelatedUser() {
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .build());

        assertThatThrownBy(() -> service.assertApplicationParticipant(99L, 30L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问此申请");
    }

    @Test
    void transcribeVoiceApplicationUsesCachedTranscriptWithoutCallingMimo() {
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .appType(2)
                .voiceTranscript("已经转写")
                .build());

        String result = service.transcribeVoiceApplication(99L, 20L, "zh");

        assertThat(result).isEqualTo("已经转写");
        verifyNoInteractions(voiceApplicationService);
    }

    @Test
    void transcribeVoiceApplicationRejectsOutsiderBeforeTouchingAudio() {
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .appType(2)
                .build());

        assertThatThrownBy(() -> service.transcribeVoiceApplication(99L, 30L, "zh"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问此申请");
        verifyNoInteractions(applicationAttachmentService, voiceApplicationService);
    }

    @Test
    void transcribeVoiceApplicationRejectsNonVoiceApplication() {
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .appType(1)
                .build());

        assertThatThrownBy(() -> service.transcribeVoiceApplication(99L, 20L, "zh"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不是语音申请");
    }

    @Test
    void transcribeVoiceApplicationRejectsUnexpectedVoiceUrl() {
        ApplicationAttachment attachment = ApplicationAttachment.builder()
                .id(7L)
                .applicationId(99L)
                .fileName("voice.wav")
                .fileUrl("http://127.0.0.1/internal/voice.wav")
                .fileType("audio/wav")
                .build();
        when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .appType(2)
                .build());
        when(applicationAttachmentService.list(any(Wrapper.class))).thenReturn(List.of(attachment));
        when(ossProperties.getBucketUrl()).thenReturn("https://trusted-bucket.example");

        assertThatThrownBy(() -> service.transcribeVoiceApplication(99L, 20L, "zh"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("语音文件来源不可信");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void finishedVoiceUploadMovesDraftToPendingAndAnnouncesIt() {
        when(applicationMapper.selectById(99L)).thenReturn(uploadingDraft());

        service.processVoiceApplicationAudioAsync(99L, 10L, new byte[]{1, 2}, "voice.wav", "audio/wav", 2L);

        ArgumentCaptor<Application> updated = ArgumentCaptor.forClass(Application.class);
        verify(applicationMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(ApplicationStatusEnum.PENDING.getCode());
        assertThat(updated.getValue().getVoiceStatus()).isEqualTo(VoiceStatusEnum.READY.getCode());
        verify(eventPublisher).publishEvent(new ApplicationSubmittedEvent(99L, 10L, 20L, "语音申请", false));
    }

    @Test
    void failedVoiceUploadKeepsDraftAndStoresOnlyAUserFacingReason() {
        when(applicationMapper.selectById(99L)).thenReturn(uploadingDraft());
        when(applicationAttachmentService.uploadApplicationAttachment(eq(99L), any(byte[].class), any(), any(), anyLong()))
                .thenThrow(new IllegalStateException("oss endpoint 10.0.0.8 refused"));

        service.processVoiceApplicationAudioAsync(99L, 10L, new byte[]{1, 2}, "voice.wav", "audio/wav", 2L);

        ArgumentCaptor<Application> updated = ArgumentCaptor.forClass(Application.class);
        verify(applicationMapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(ApplicationStatusEnum.DRAFT.getCode());
        assertThat(updated.getValue().getVoiceStatus()).isEqualTo(VoiceStatusEnum.FAILED.getCode());
        assertThat(updated.getValue().getVoiceUploadError()).isEqualTo("语音上传失败");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private Application uploadingDraft() {
        return Application.builder()
                .id(99L)
                .applicantId(10L)
                .approverId(20L)
                .appType(2)
                .title("语音申请")
                .status(ApplicationStatusEnum.DRAFT.getCode())
                .voiceStatus(VoiceStatusEnum.UPLOADING.getCode())
                .build();
    }
}
