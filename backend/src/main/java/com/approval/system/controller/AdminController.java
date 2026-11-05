package com.approval.system.controller;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.UserStatusEnum;
import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.response.ApiResponse;
import com.approval.system.common.security.ActiveUserChecker;
import com.approval.system.dto.AdminUserUpdateRequest;
import com.approval.system.dto.EmailSettingsResponse;
import com.approval.system.dto.EmailSettingsUpdateRequest;
import com.approval.system.dto.MimoConfigResponse;
import com.approval.system.dto.VoiceModelSettingsResponse;
import com.approval.system.dto.VoiceModelSettingsUpdateRequest;
import com.approval.system.entity.Application;
import com.approval.system.entity.Notification;
import com.approval.system.entity.User;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.ISystemSettingService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台管理接口。访问控制由 SecurityConfig（/api/admin/** 需要 ROLE_ADMIN）统一处理，
 * 这里的 @PreAuthorize 是同一规则的第二道保险，防止以后改 URL 规则时漏掉。
 * 注意：普通用户也会用的 /api/admin/user/stats 在 {@link UserStatsController}。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private ISystemSettingService systemSettingService;

    @Autowired
    private ActiveUserChecker activeUserChecker;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard/stats")
    public ApiResponse<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalApplications", applicationService.count());
        stats.put("pendingApplications", countApplications(ApplicationStatusEnum.PENDING));
        stats.put("approvedApplications", countApplications(ApplicationStatusEnum.APPROVED));
        stats.put("rejectedApplications", countApplications(ApplicationStatusEnum.REJECTED));
        stats.put("totalUsers", userService.count());
        stats.put("activeUsers", userService.count(
                new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatusEnum.ENABLED.getCode())));
        return ApiResponse.success(stats);
    }

    /**
     * 获取所有用户列表（管理员用）
     */
    @GetMapping("/users")
    public ApiResponse<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<User> page = userService.getAllUsers(pageNum, pageSize);
        // 隐藏密码
        page.getRecords().forEach(user -> user.setPassword(null));
        return ApiResponse.success(page);
    }

    /**
     * 获取所有申请列表（管理员用）
     */
    @GetMapping("/applications")
    public ApiResponse<Page<Application>> getAllApplications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Application::getStatus, status);
        }
        wrapper.orderByDesc(Application::getCreatedAt);
        Page<Application> page = applicationService.page(new Page<>(pageNum, pageSize), wrapper);
        applicationService.fillUserNames(page.getRecords());
        return ApiResponse.success(page);
    }

    /**
     * 获取所有通知列表（管理员用）
     */
    @GetMapping("/notifications")
    public ApiResponse<Page<Notification>> getAllNotifications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer sendStatus,
            @RequestParam(required = false) Integer notifyType) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        if (sendStatus != null) {
            wrapper.eq(Notification::getSendStatus, sendStatus);
        }
        if (notifyType != null) {
            wrapper.eq(Notification::getNotifyType, notifyType);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        return ApiResponse.success(notificationService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 更新用户状态（管理员用）
     */
    @PutMapping("/users/{userId}/status")
    public ApiResponse<User> updateUserStatus(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long userId,
            @RequestParam @NotNull @Min(value = 0, message = "用户状态只能是 0 或 1")
            @Max(value = 1, message = "用户状态只能是 0 或 1") Integer status) {
        // 防止管理员把自己禁用后锁在门外
        if (userId.equals(adminId)) {
            throw BusinessException.badRequest("不能修改自己的账号状态");
        }

        User user = requireUser(userId);
        user.setStatus(status);
        userService.updateById(user);
        // 让已登录的 token 立即按新状态校验
        activeUserChecker.evict(userId);
        user.setPassword(null);
        log.info("管理员更新用户状态，userId: {}, status: {}", userId, status);
        return ApiResponse.success(user);
    }

    /**
     * 更新用户角色（管理员用）
     */
    @PutMapping("/users/{userId}/role")
    public ApiResponse<User> updateUserRole(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long userId,
            @RequestParam @NotNull @Min(value = 0, message = "用户角色只能是 0 或 1")
            @Max(value = 1, message = "用户角色只能是 0 或 1") Integer role) {
        // 防止管理员把自己降级后失去后台权限
        if (userId.equals(adminId)) {
            throw BusinessException.badRequest("不能修改自己的角色");
        }

        User user = requireUser(userId);
        user.setRole(role);
        userService.updateById(user);
        // 角色也缓存在认证快照里，立即刷新
        activeUserChecker.evict(userId);
        user.setPassword(null);
        log.info("管理员更新用户角色，userId: {}, role: {}", userId, role);
        return ApiResponse.success(user);
    }

    /**
     * 更新用户语音通知权限（管理员用）
     */
    @PutMapping("/users/{userId}/voice-notification")
    public ApiResponse<User> updateVoiceNotificationPermission(
            @PathVariable Long userId,
            @RequestParam Boolean enabled) {
        User user = requireUser(userId);
        user.setVoiceNotificationEnabled(enabled);
        userService.updateById(user);
        user.setPassword(null);
        log.info("管理员更新用户语音通知权限，userId: {}, enabled: {}", userId, enabled);
        return ApiResponse.success(user);
    }

    /**
     * 更新用户基本信息（管理员用）。只更新请求里出现（非 null）的字段。
     */
    @PutMapping("/users/{userId}")
    public ApiResponse<User> updateUserInfo(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest updates) {
        User user = requireUser(userId);

        if (updates.getRealName() != null) {
            user.setRealName(updates.getRealName());
        }
        if (updates.getPhone() != null) {
            String phone = updates.getPhone();
            User existingUser = userService.getUserByPhone(phone);
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                throw BusinessException.conflict("该手机号已被其他用户使用");
            }
            user.setPhone(phone);
        }
        if (updates.getEmail() != null) {
            String email = updates.getEmail();
            if (!email.isEmpty()) {
                User existingUser = userService.getUserByEmail(email);
                if (existingUser != null && !existingUser.getId().equals(userId)) {
                    throw BusinessException.conflict("该邮箱已被其他用户使用");
                }
            }
            user.setEmail(email);
        }

        userService.updateById(user);
        user.setPassword(null);
        log.info("管理员更新用户信息，userId: {}", userId);
        return ApiResponse.success(user);
    }

    /**
     * 管理员批准申请：不要求管理员是该申请指定的审批人
     */
    @PostMapping("/applications/{applicationId}/approve")
    public ApiResponse<Void> adminApproveApplication(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long applicationId,
            @RequestParam(required = false) @Size(max = 500, message = "审批意见不能超过500个字符") String approvalDetail) {
        applicationService.adminApproveApplication(applicationId, adminId,
                StringUtils.hasText(approvalDetail) ? approvalDetail.trim() : "管理员批准");
        log.info("管理员批准申请，applicationId: {}, adminId: {}", applicationId, adminId);
        return ApiResponse.success(null);
    }

    /**
     * 管理员驳回申请：不要求管理员是该申请指定的审批人
     */
    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<Void> adminRejectApplication(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long applicationId,
            @RequestParam(required = false) @Size(max = 500, message = "驳回原因不能超过500个字符") String rejectReason) {
        applicationService.adminRejectApplication(applicationId, adminId,
                StringUtils.hasText(rejectReason) ? rejectReason.trim() : "管理员驳回");
        log.info("管理员驳回申请，applicationId: {}, adminId: {}", applicationId, adminId);
        return ApiResponse.success(null);
    }

    /**
     * 获取邮件发送配置（管理员用）
     */
    @GetMapping("/settings/email")
    public ApiResponse<EmailSettingsResponse> getEmailSettings() {
        return ApiResponse.success(systemSettingService.getEmailSettings());
    }

    /**
     * 更新邮件发送配置（管理员用）
     */
    @PutMapping("/settings/email")
    public ApiResponse<EmailSettingsResponse> updateEmailSettings(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody EmailSettingsUpdateRequest request) {
        EmailSettingsResponse response = systemSettingService.updateEmailSettings(request, adminId);
        log.info("管理员更新邮件发送配置，adminId: {}", adminId);
        return ApiResponse.success(response);
    }

    /**
     * 获取语音大模型配置（管理员用）
     */
    @GetMapping("/settings/voice")
    public ApiResponse<VoiceModelSettingsResponse> getVoiceModelSettings() {
        return ApiResponse.success(systemSettingService.getVoiceModelSettings());
    }

    /**
     * 更新语音大模型配置（管理员用）
     */
    @PutMapping("/settings/voice")
    public ApiResponse<VoiceModelSettingsResponse> updateVoiceModelSettings(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody VoiceModelSettingsUpdateRequest request) {
        VoiceModelSettingsResponse response = systemSettingService.updateVoiceModelSettings(request, adminId);
        log.info("管理员更新语音大模型配置，adminId: {}", adminId);
        return ApiResponse.success(response);
    }

    /**
     * 获取 MiMo 配置（管理员用）
     */
    @GetMapping("/mimo/config")
    public ApiResponse<MimoConfigResponse> getMimoConfig() {
        VoiceModelSettingsResponse voiceSettings = systemSettingService.getVoiceModelSettings();
        MimoConfigResponse response = MimoConfigResponse.builder()
                .enabled(voiceSettings.getEnabled())
                .baseUrl(voiceSettings.getAsrBaseUrl())
                .asrModel(voiceSettings.getAsrModel())
                .chatModel(voiceSettings.getChatModel())
                .apiKeyConfigured(voiceSettings.getAsrApiKeyConfigured())
                .apiKeyMasked(voiceSettings.getAsrApiKeyMasked())
                .build();
        return ApiResponse.success(response);
    }

    private long countApplications(ApplicationStatusEnum status) {
        return applicationService.count(
                new LambdaQueryWrapper<Application>().eq(Application::getStatus, status.getCode()));
    }

    private User requireUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }
}
