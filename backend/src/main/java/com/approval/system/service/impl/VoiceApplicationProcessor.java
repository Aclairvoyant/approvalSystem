package com.approval.system.service.impl;

import com.approval.system.common.config.AliyunOssProperties;
import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.ApplicationTypeEnum;
import com.approval.system.common.enums.OperationTypeEnum;
import com.approval.system.common.enums.VoiceStatusEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.entity.Application;
import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.service.IApplicationAttachmentService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IVoiceApplicationService;
import com.approval.system.service.notification.ApplicationSubmittedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语音申请的流程：创建草稿、校验并异步上传音频、音频就绪后转为待审批，以及按需转写文字。
 * 由 {@link ApplicationServiceImpl} 调用；事务边界在 ApplicationServiceImpl 的公开方法上。
 */
@Slf4j
@Component
public class VoiceApplicationProcessor {

    static final String VOICE_APPLICATION_TITLE = "语音申请";
    static final String VOICE_APPLICATION_DESCRIPTION = "语音申请，请播放语音内容";
    private static final int MAX_UPLOAD_ERROR_LENGTH = 500;

    @Autowired
    private ApplicationMapper applicationMapper;

    @Autowired
    private IOperationLogService operationLogService;

    @Autowired
    private IApplicationAttachmentService applicationAttachmentService;

    @Autowired
    private IVoiceApplicationService voiceApplicationService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AliyunOssProperties ossProperties;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 一次性创建带音频的语音申请（直接进入待审批）
     */
    public Application createWithAudio(Long applicantId, Long approverId, MultipartFile audio) {
        validateAudio(audio);

        Application application = Application.builder()
                .applicantId(applicantId)
                .approverId(approverId)
                .appType(ApplicationTypeEnum.VOICE.getCode())
                .title(VOICE_APPLICATION_TITLE)
                .description(VOICE_APPLICATION_DESCRIPTION)
                .status(ApplicationStatusEnum.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        applicationMapper.insert(application);

        operationLogService.record(application.getId(), applicantId, OperationTypeEnum.CREATE,
                null, ApplicationStatusEnum.PENDING.getCode(), "创建语音申请");

        applicationAttachmentService.uploadApplicationAttachment(application.getId(), audio);
        return application;
    }

    /**
     * 创建语音草稿，等待音频上传完成后再进入待审批
     */
    public Application createDraft(Long applicantId, Long approverId) {
        Application application = Application.builder()
                .applicantId(applicantId)
                .approverId(approverId)
                .appType(ApplicationTypeEnum.VOICE.getCode())
                .title(VOICE_APPLICATION_TITLE)
                .description(VOICE_APPLICATION_DESCRIPTION)
                .voiceStatus(VoiceStatusEnum.UPLOADING.getCode())
                .status(ApplicationStatusEnum.DRAFT.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        applicationMapper.insert(application);

        operationLogService.record(application.getId(), applicantId, OperationTypeEnum.CREATE,
                null, ApplicationStatusEnum.DRAFT.getCode(), "创建语音草稿");
        return application;
    }

    /**
     * 同步校验上传请求并把草稿标记为"上传中"，真正的上传交给 {@link #processAudioAsync}
     */
    public void prepareUpload(Long applicationId, Long applicantId, MultipartFile audio) {
        validateAudio(audio);

        Application application = requireApplication(applicationId);
        if (!application.getApplicantId().equals(applicantId)) {
            throw BusinessException.forbidden("没有权限上传音频");
        }
        if (!ApplicationTypeEnum.VOICE.is(application.getAppType())) {
            throw BusinessException.badRequest("只有语音应用可以上传语音音频");
        }
        if (!ApplicationStatusEnum.DRAFT.is(application.getStatus())) {
            throw BusinessException.badRequest("只有草稿语音应用可以上传音频");
        }

        Integer voiceStatus = application.getVoiceStatus();
        if (voiceStatus != null
                && !VoiceStatusEnum.UPLOADING.is(voiceStatus)
                && !VoiceStatusEnum.FAILED.is(voiceStatus)) {
            throw BusinessException.badRequest("语音应用音频无法上传");
        }

        application.setVoiceStatus(VoiceStatusEnum.UPLOADING.getCode());
        application.setVoiceUploadError(null);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
    }

    /**
     * 异步上传音频到 OSS；成功后申请进入待审批并通知审批人，失败则回到草稿并记录原因。
     */
    @Async
    public void processAudioAsync(Long applicationId, Long applicantId, byte[] audioBytes,
                                  String fileName, String contentType, long fileSize) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            log.warn("申请为空, applicationId: {}", applicationId);
            return;
        }
        if (!application.getApplicantId().equals(applicantId)) {
            log.warn("申请人不匹配, applicationId: {}, applicantId: {}", applicationId, applicantId);
            return;
        }
        if (!isUploadOpen(application, applicantId)) {
            log.warn("跳过语音上传，因为应用程序已不再开放上传, applicationId: {}", applicationId);
            return;
        }

        try {
            applicationAttachmentService.uploadApplicationAttachment(applicationId, audioBytes, fileName, contentType, fileSize);
            Application latest = applicationMapper.selectById(applicationId);
            if (!isUploadOpen(latest, applicantId)) {
                log.warn("跳过语音上传成功更新，因为应用状态已更改, applicationId: {}", applicationId);
                return;
            }
            latest.setStatus(ApplicationStatusEnum.PENDING.getCode());
            latest.setVoiceStatus(VoiceStatusEnum.READY.getCode());
            latest.setVoiceUploadError(null);
            latest.setUpdatedAt(LocalDateTime.now());
            applicationMapper.updateById(latest);
            eventPublisher.publishEvent(new ApplicationSubmittedEvent(
                    latest.getId(), latest.getApplicantId(), latest.getApproverId(), latest.getTitle(), false));
        } catch (Exception e) {
            Application latest = applicationMapper.selectById(applicationId);
            if (isUploadOpen(latest, applicantId)) {
                latest.setStatus(ApplicationStatusEnum.DRAFT.getCode());
                latest.setVoiceStatus(VoiceStatusEnum.FAILED.getCode());
                latest.setVoiceUploadError(uploadErrorMessage(e));
                latest.setUpdatedAt(LocalDateTime.now());
                applicationMapper.updateById(latest);
            }
            log.error("处理语音应用音频失败, applicationId: {}", applicationId, e);
        }
    }

    /**
     * 转写语音申请（调用方已校验访问权限）。已转写过时直接返回缓存文本。
     */
    public String transcribe(Application application, String language) {
        if (!ApplicationTypeEnum.VOICE.is(application.getAppType())) {
            throw BusinessException.badRequest("不是语音申请");
        }
        if (StringUtils.hasText(application.getVoiceTranscript())) {
            return application.getVoiceTranscript();
        }

        ApplicationAttachment audioAttachment = findVoiceAttachment(application.getId());
        byte[] audioBytes = downloadAudio(audioAttachment);
        String transcript = voiceApplicationService.transcribe(
                audioBytes,
                audioAttachment.getFileType(),
                audioAttachment.getFileName(),
                language
        );
        if (!StringUtils.hasText(transcript)) {
            throw BusinessException.badRequest("未能识别出语音内容，请重试");
        }

        application.setVoiceTranscript(transcript);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        return transcript;
    }

    /**
     * 校验语音文件：非空、WAV、不超过 OSS 单文件上限。审批时的语音回复也走这里。
     */
    public void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw BusinessException.badRequest("语音内容为空");
        }

        String contentType = audio.getContentType();
        String filename = audio.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.toLowerCase();
        boolean wavAudio = lowerName.endsWith(".wav")
                || "audio/wav".equals(contentType)
                || "audio/x-wav".equals(contentType)
                || "audio/wave".equals(contentType);
        if (!wavAudio) {
            throw BusinessException.badRequest("仅支持 WAV 语音文件");
        }

        Long maxFileSize = ossProperties == null ? null : ossProperties.getMaxFileSize();
        if (maxFileSize != null && maxFileSize > 0 && audio.getSize() > maxFileSize) {
            throw BusinessException.badRequest("语音文件大小超过限制");
        }
    }

    private Application requireApplication(Long applicationId) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw BusinessException.notFound("申请单不存在");
        }
        return application;
    }

    private boolean isUploadOpen(Application application, Long applicantId) {
        return application != null
                && application.getApplicantId().equals(applicantId)
                && ApplicationStatusEnum.DRAFT.is(application.getStatus())
                && VoiceStatusEnum.UPLOADING.is(application.getVoiceStatus());
    }

    /**
     * 失败原因会展示给申请人，只保留业务提示，其他异常用通用文案
     */
    private String uploadErrorMessage(Exception e) {
        String message = e instanceof BusinessException ? e.getMessage() : null;
        if (!StringUtils.hasText(message)) {
            return "语音上传失败";
        }
        return message.length() > MAX_UPLOAD_ERROR_LENGTH ? message.substring(0, MAX_UPLOAD_ERROR_LENGTH) : message;
    }

    private ApplicationAttachment findVoiceAttachment(Long applicationId) {
        List<ApplicationAttachment> attachments = applicationAttachmentService.list(
                new LambdaQueryWrapper<ApplicationAttachment>()
                        .eq(ApplicationAttachment::getApplicationId, applicationId)
                        .likeRight(ApplicationAttachment::getFileType, "audio/")
                        .orderByDesc(ApplicationAttachment::getCreatedAt));
        if (attachments == null || attachments.isEmpty()) {
            throw BusinessException.notFound("未找到语音文件");
        }
        return attachments.get(0);
    }

    private byte[] downloadAudio(ApplicationAttachment attachment) {
        validateTrustedVoiceUrl(attachment.getFileUrl());
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(attachment.getFileUrl(), byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
                throw new BusinessException(BusinessException.SERVICE_UNAVAILABLE, "读取语音文件失败");
            }
            return response.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载语音文件失败，attachmentId: {}", attachment.getId(), e);
            throw new BusinessException(BusinessException.SERVICE_UNAVAILABLE, "读取语音文件失败", e);
        }
    }

    private void validateTrustedVoiceUrl(String fileUrl) {
        String bucketUrl = ossProperties == null ? null : ossProperties.getBucketUrl();
        if (!StringUtils.hasText(bucketUrl) || !StringUtils.hasText(fileUrl)) {
            return;
        }
        String normalizedBucketUrl = bucketUrl.endsWith("/") ? bucketUrl : bucketUrl + "/";
        if (!fileUrl.startsWith(normalizedBucketUrl)) {
            throw BusinessException.badRequest("语音文件来源不可信");
        }
    }
}
