package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.FileUploadResponse;
import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.entity.ApprovalAttachment;
import com.approval.system.service.IApplicationAttachmentService;
import com.approval.system.service.IApprovalAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * 上传申请附件
     */
    @PostMapping("/application/{applicationId}")
    public ApiResponse<FileUploadResponse> uploadApplicationAttachment(
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file) {
        try {
            ApplicationAttachment attachment = applicationAttachmentService.uploadApplicationAttachment(applicationId, file);

            FileUploadResponse response = FileUploadResponse.builder()
                    .attachmentId(attachment.getId())
                    .fileName(attachment.getFileName())
                    .fileUrl(attachment.getFileUrl())
                    .fileSize(attachment.getFileSize())
                    .fileType(attachment.getFileType())
                    .build();

            return ApiResponse.success("附件上传成功", response);
        } catch (Exception e) {
            log.error("上传申请附件失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 上传审批附件
     */
    @PostMapping("/approval/{applicationId}")
    public ApiResponse<FileUploadResponse> uploadApprovalAttachment(
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ApprovalAttachment attachment = approvalAttachmentService.uploadApprovalAttachment(applicationId, userId, file);

            FileUploadResponse response = FileUploadResponse.builder()
                    .attachmentId(attachment.getId())
                    .fileName(attachment.getFileName())
                    .fileUrl(attachment.getFileUrl())
                    .fileSize(attachment.getFileSize())
                    .fileType(attachment.getFileType())
                    .build();

            return ApiResponse.success("附件上传成功", response);
        } catch (Exception e) {
            log.error("上传审批附件失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取申请附件列表
     */
    @GetMapping("/application/{applicationId}")
    public ApiResponse<List<FileUploadResponse>> getApplicationAttachments(@PathVariable Long applicationId) {
        try {
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
        } catch (Exception e) {
            log.error("获取申请附件列表失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取审批附件列表
     */
    @GetMapping("/approval/{applicationId}")
    public ApiResponse<List<FileUploadResponse>> getApprovalAttachments(@PathVariable Long applicationId) {
        try {
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
        } catch (Exception e) {
            log.error("获取审批附件列表失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 删除申请附件
     */
    @DeleteMapping("/application/{attachmentId}")
    public ApiResponse<Void> deleteApplicationAttachment(@PathVariable Long attachmentId) {
        try {
            applicationAttachmentService.deleteAttachment(attachmentId);
            return ApiResponse.success("附件已删除");
        } catch (Exception e) {
            log.error("删除申请附件失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 删除审批附件
     */
    @DeleteMapping("/approval/{attachmentId}")
    public ApiResponse<Void> deleteApprovalAttachment(@PathVariable Long attachmentId) {
        try {
            approvalAttachmentService.deleteAttachment(attachmentId);
            return ApiResponse.success("附件已删除");
        } catch (Exception e) {
            log.error("删除审批附件失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
