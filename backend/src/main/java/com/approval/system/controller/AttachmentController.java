package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.FileUploadResponse;
import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.entity.ApprovalAttachment;
import com.approval.system.service.IApplicationAttachmentService;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IApprovalAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    @Autowired
    private IApplicationAttachmentService applicationAttachmentService;

    @Autowired
    private IApprovalAttachmentService approvalAttachmentService;

    @Autowired
    private IApplicationService applicationService;

    /**
     * 上传申请附件
     */
    @PostMapping("/application/{applicationId}")
    public ApiResponse<FileUploadResponse> uploadApplicationAttachment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file) {
        applicationService.assertApplicationApplicant(applicationId, userId);
        ApplicationAttachment attachment = applicationAttachmentService.uploadApplicationAttachment(applicationId, file);

        FileUploadResponse response = FileUploadResponse.builder()
                .attachmentId(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .build();

        return ApiResponse.success("附件上传成功", response);
    }

    /**
     * 上传审批附件
     */
    @PostMapping("/approval/{applicationId}")
    public ApiResponse<FileUploadResponse> uploadApprovalAttachment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file) {
        applicationService.assertApplicationApprover(applicationId, userId);
        ApprovalAttachment attachment = approvalAttachmentService.uploadApprovalAttachment(applicationId, userId, file);

        FileUploadResponse response = FileUploadResponse.builder()
                .attachmentId(attachment.getId())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .build();

        return ApiResponse.success("附件上传成功", response);
    }

    /**
     * 获取申请附件列表
     */
    @GetMapping("/application/{applicationId}")
    public ApiResponse<List<FileUploadResponse>> getApplicationAttachments(@AuthenticationPrincipal Long userId, @PathVariable Long applicationId) {
        applicationService.assertApplicationParticipant(applicationId, userId);
        List<ApplicationAttachment> attachments = applicationAttachmentService.getApplicationAttachments(applicationId);

        List<FileUploadResponse> responses = attachments.stream()
                .map(a -> FileUploadResponse.builder()
                        .attachmentId(a.getId())
                        .fileName(a.getFileName())
                        .fileUrl(a.getFileUrl())
                        .fileSize(a.getFileSize())
                        .fileType(a.getFileType())
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(responses);
    }

    /**
     * 获取审批附件列表
     */
    @GetMapping("/approval/{applicationId}")
    public ApiResponse<List<FileUploadResponse>> getApprovalAttachments(@AuthenticationPrincipal Long userId, @PathVariable Long applicationId) {
        applicationService.assertApplicationParticipant(applicationId, userId);
        List<ApprovalAttachment> attachments = approvalAttachmentService.getApprovalAttachments(applicationId);

        List<FileUploadResponse> responses = attachments.stream()
                .map(a -> FileUploadResponse.builder()
                        .attachmentId(a.getId())
                        .fileName(a.getFileName())
                        .fileUrl(a.getFileUrl())
                        .fileSize(a.getFileSize())
                        .fileType(a.getFileType())
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(responses);
    }

    /**
     * 删除申请附件
     */
    @DeleteMapping("/application/{attachmentId}")
    public ApiResponse<Void> deleteApplicationAttachment(@AuthenticationPrincipal Long userId, @PathVariable Long attachmentId) {
        ApplicationAttachment attachment = applicationAttachmentService.getById(attachmentId);
        if (attachment != null) {
            applicationService.assertApplicationApplicant(attachment.getApplicationId(), userId);
        }
        applicationAttachmentService.deleteAttachment(attachmentId);
        return ApiResponse.success("附件已删除");
    }

    /**
     * 删除审批附件
     */
    @DeleteMapping("/approval/{attachmentId}")
    public ApiResponse<Void> deleteApprovalAttachment(@AuthenticationPrincipal Long userId, @PathVariable Long attachmentId) {
        ApprovalAttachment attachment = approvalAttachmentService.getById(attachmentId);
        if (attachment != null && !attachment.getOperatorId().equals(userId)) {
            throw BusinessException.forbidden("无权删除此附件");
        }
        approvalAttachmentService.deleteAttachment(attachmentId);
        return ApiResponse.success("附件已删除");
    }
}
