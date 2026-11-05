package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationApprovalRequest;
import com.approval.system.dto.ApplicationCreateRequest;
import com.approval.system.dto.ApplicationUpdateRequest;
import com.approval.system.dto.VoiceApplicationCreateRequest;
import com.approval.system.dto.VoiceParseResult;
import com.approval.system.entity.Application;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IOperationLogService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IVoiceApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private static final String DETAIL_TOO_LONG = "审批意见不能超过500个字符";

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private IOperationLogService operationLogService;

    @Autowired
    private IVoiceApplicationService voiceApplicationService;

    /**
     * 语音解析申请内容：上传口述音频，返回 ASR 转写文字及抽取出的标题/描述/备注。
     * 仅做解析回填，不创建申请；用户核对、选择审批人后再走创建接口。
     */
    @PostMapping(value = "/voice-parse", consumes = "multipart/form-data")
    public ApiResponse<VoiceParseResult> parseVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        VoiceParseResult result = voiceApplicationService.parseVoice(file, language);
        return ApiResponse.success("解析成功", result);
    }

    /**
     * 创建语音消息式申请单。申请人只提交一段语音，标题由后端生成。
     */
    @PostMapping(value = "/voice", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Application> createVoiceApplicationDraft(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody VoiceApplicationCreateRequest request) {
        assertRelated(userId, request.getApproverId());
        Application application = applicationService.createVoiceApplicationDraft(userId, request.getApproverId());
        return ApiResponse.success("语音应用草稿已创建", application);
    }

    @PostMapping(value = "/voice", consumes = "multipart/form-data")
    public ApiResponse<Application> createVoiceApplication(
            @AuthenticationPrincipal Long userId,
            @RequestParam("approverId") Long approverId,
            @RequestParam("file") MultipartFile file) throws IOException {
        assertRelated(userId, approverId);

        Application application = applicationService.createVoiceApplicationDraft(userId, approverId);
        applicationService.validateVoiceApplicationAudioUpload(application.getId(), userId, file);
        applicationService.processVoiceApplicationAudioAsync(
                application.getId(),
                userId,
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
        return ApiResponse.success("语音申请创建成功", application);
    }

    /**
     * 为语音草稿上传音频，异步处理
     */
    @PostMapping(value = "/{id}/voice-audio", consumes = "multipart/form-data")
    public ApiResponse<Void> uploadVoiceApplicationAudio(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        applicationService.validateVoiceApplicationAudioUpload(id, userId, file);
        applicationService.processVoiceApplicationAudioAsync(
                id,
                userId,
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
        return ApiResponse.success("语音申请音频上传成功，正在处理中");
    }

    /**
     * 创建申请单
     */
    @PostMapping
    public ApiResponse<Application> createApplication(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ApplicationCreateRequest request) {
        // 验证双方是否互为对象
        assertRelated(userId, request.getApproverId());

        Application application = applicationService.createApplication(
                userId, request.getApproverId(), request.getTitle(),
                request.getDescription(), request.getRemark(), request.getSendVoiceNotification());
        return ApiResponse.success("申请创建成功", application);
    }

    /**
     * 手动发送语音通知给审批人
     */
    @PostMapping("/{id}/send-voice-notification")
    public ApiResponse<Void> sendVoiceNotification(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        if (!applicationService.sendVoiceNotificationToApprover(id, userId)) {
            throw BusinessException.badRequest("语音通知发送失败，请检查审批人手机号是否填写或申请状态是否为待审批");
        }
        return ApiResponse.success("语音通知发送成功");
    }

    /**
     * 语音申请转文字。已转写过时返回缓存文本。
     */
    @PostMapping("/{id}/transcribe-voice")
    public ApiResponse<String> transcribeVoiceApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(value = "language", required = false, defaultValue = "zh") String language) {
        String transcript = applicationService.transcribeVoiceApplication(id, userId, language);
        return ApiResponse.success("转写成功", transcript);
    }

    /**
     * 审批通过（可附带语音回复）
     */
    @PostMapping(value = "/{id}/approve", consumes = "multipart/form-data")
    public ApiResponse<Void> approveApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(value = "approvalDetail", required = false) @Size(max = 500, message = DETAIL_TOO_LONG) String approvalDetail,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        applicationService.approveApplication(id, userId, approvalDetail, file);
        return ApiResponse.success("审批通过");
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationApprovalRequest request) {
        applicationService.approveApplication(id, userId, request.getApprovalDetail());
        return ApiResponse.success("审批通过");
    }

    /**
     * 驳回申请（可附带语音回复）
     */
    @PostMapping(value = "/{id}/reject", consumes = "multipart/form-data")
    public ApiResponse<Void> rejectApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(value = "approvalDetail", required = false) @Size(max = 500, message = DETAIL_TOO_LONG) String approvalDetail,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        applicationService.rejectApplication(id, userId, approvalDetail, file);
        return ApiResponse.success("申请已驳回");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> rejectApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationApprovalRequest request) {
        applicationService.rejectApplication(id, userId, request.getApprovalDetail());
        return ApiResponse.success("申请已驳回");
    }

    /**
     * 获取申请人的申请列表
     */
    @GetMapping("/my-applications")
    public ApiResponse<Page<Application>> getMyApplications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(applicationService.getApplicantApplications(userId, status, pageNum, pageSize));
    }

    /**
     * 获取待审批列表
     */
    @GetMapping("/pending")
    public ApiResponse<Page<Application>> getPendingApplications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(applicationService.getApproverPendingApplications(userId, keyword, pageNum, pageSize));
    }

    /**
     * 获取申请详情（仅申请人、审批人可见）
     */
    @GetMapping("/{id}")
    public ApiResponse<Application> getApplicationDetail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        applicationService.assertApplicationParticipant(id, userId);
        Application application = applicationService.getApplicationDetail(id);
        if (application == null) {
            throw BusinessException.notFound("申请不存在");
        }
        return ApiResponse.success(application);
    }

    /**
     * 更新申请（仅待审批状态可修改）
     */
    @PutMapping("/{id}")
    public ApiResponse<Application> updateApplication(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        Application application = applicationService.updateApplication(
                id, userId, request.getTitle(), request.getDescription(), request.getRemark());
        return ApiResponse.success("修改成功", application);
    }

    /**
     * 取消/撤回申请（仅待审批状态可取消）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelApplication(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        applicationService.cancelApplication(id, userId);
        return ApiResponse.success("申请已取消");
    }

    /**
     * 获取我审批过的申请列表
     */
    @GetMapping("/my-approvals")
    public ApiResponse<Page<Application>> getMyApprovals(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(applicationService.getMyApprovedApplications(userId, status, pageNum, pageSize));
    }

    private void assertRelated(Long userId, Long approverId) {
        if (!userRelationService.isRelated(userId, approverId)) {
            throw BusinessException.badRequest("只有互为对象的用户才能创建申请");
        }
    }
}
