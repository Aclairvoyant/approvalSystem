package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.config.MiMoConfig;
import com.approval.system.dto.MimoConfigResponse;
import com.approval.system.entity.Application;
import com.approval.system.entity.Notification;
import com.approval.system.entity.User;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.INotificationService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private MiMoConfig miMoConfig;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard/stats")
    public ApiResponse<Map<String, Object>> getDashboardStats() {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            Map<String, Object> stats = new HashMap<>();

            // 获取申请统计
            long totalApplications = applicationService.count();
            long pendingApplications = applicationService.count(
                    new LambdaQueryWrapper<Application>().eq(Application::getStatus, 1));
            long approvedApplications = applicationService.count(
                    new LambdaQueryWrapper<Application>().eq(Application::getStatus, 2));
            long rejectedApplications = applicationService.count(
                    new LambdaQueryWrapper<Application>().eq(Application::getStatus, 3));

            // 获取用户统计
            long totalUsers = userService.count();
            long activeUsers = userService.count(
                    new LambdaQueryWrapper<User>().eq(User::getStatus, 1));

            stats.put("totalApplications", totalApplications);
            stats.put("pendingApplications", pendingApplications);
            stats.put("approvedApplications", approvedApplications);
            stats.put("rejectedApplications", rejectedApplications);
            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取仪表盘统计失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 获取用户个人统计数据
     */
    @GetMapping("/user/stats")
    public ApiResponse<Map<String, Object>> getUserStats() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            Map<String, Object> stats = new HashMap<>();

            // 我发起的申请统计
            long myPending = applicationService.count(
                    new LambdaQueryWrapper<Application>()
                            .eq(Application::getApplicantId, userId)
                            .eq(Application::getStatus, 1));
            long myApproved = applicationService.count(
                    new LambdaQueryWrapper<Application>()
                            .eq(Application::getApplicantId, userId)
                            .eq(Application::getStatus, 2));
            long myRejected = applicationService.count(
                    new LambdaQueryWrapper<Application>()
                            .eq(Application::getApplicantId, userId)
                            .eq(Application::getStatus, 3));

            // 待我审批的申请
            long pendingForMe = applicationService.count(
                    new LambdaQueryWrapper<Application>()
                            .eq(Application::getApproverId, userId)
                            .eq(Application::getStatus, 1));

            // 我的对象数量
            long myRelations = userRelationService.count(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.approval.system.entity.UserRelation>()
                            .eq("user_id", userId)
                            .eq("relation_type", 2));

            stats.put("myPending", myPending);
            stats.put("myApproved", myApproved);
            stats.put("myRejected", myRejected);
            stats.put("pendingForMe", pendingForMe);
            stats.put("myRelations", myRelations);

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("获取用户统计失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 获取所有用户列表（管理员用）
     */
    @GetMapping("/users")
    public ApiResponse<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Page<User> page = userService.getAllUsers(pageNum, pageSize);
            // 隐藏密码
            page.getRecords().forEach(user -> user.setPassword(null));
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 获取所有申请列表（管理员用）
     */
    @GetMapping("/applications")
    public ApiResponse<Page<Application>> getAllApplications(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Page<Application> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
            if (status != null) {
                wrapper.eq(Application::getStatus, status);
            }
            wrapper.orderByDesc(Application::getCreatedAt);
            page = applicationService.page(page, wrapper);
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取申请列表失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
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
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Page<Notification> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
            if (sendStatus != null) {
                wrapper.eq(Notification::getSendStatus, sendStatus);
            }
            if (notifyType != null) {
                wrapper.eq(Notification::getNotifyType, notifyType);
            }
            wrapper.orderByDesc(Notification::getCreatedAt);
            page = notificationService.page(page, wrapper);
            return ApiResponse.success(page);
        } catch (Exception e) {
            log.error("获取通知列表失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新用户状态（管理员用）
     */
    @PutMapping("/users/{userId}/status")
    public ApiResponse<User> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.fail(404, "用户不存在");
            }
            user.setStatus(status);
            userService.updateById(user);
            user.setPassword(null); // 隐藏密码
            log.info("管理员更新用户状态，userId: {}, status: {}", userId, status);
            return ApiResponse.success(user);
        } catch (Exception e) {
            log.error("更新用户状态失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新用户角色（管理员用）
     */
    @PutMapping("/users/{userId}/role")
    public ApiResponse<User> updateUserRole(
            @PathVariable Long userId,
            @RequestParam Integer role) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.fail(404, "用户不存在");
            }
            user.setRole(role);
            userService.updateById(user);
            user.setPassword(null); // 隐藏密码
            log.info("管理员更新用户角色，userId: {}, role: {}", userId, role);
            return ApiResponse.success(user);
        } catch (Exception e) {
            log.error("更新用户角色失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新用户语音通知权限（管理员用）
     */
    @PutMapping("/users/{userId}/voice-notification")
    public ApiResponse<User> updateVoiceNotificationPermission(
            @PathVariable Long userId,
            @RequestParam Boolean enabled) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.fail(404, "用户不存在");
            }
            user.setVoiceNotificationEnabled(enabled);
            userService.updateById(user);
            user.setPassword(null); // 隐藏密码
            log.info("管理员更新用户语音通知权限，userId: {}, enabled: {}", userId, enabled);
            return ApiResponse.success(user);
        } catch (Exception e) {
            log.error("更新用户语音通知权限失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新用户基本信息（管理员用）
     */
    @PutMapping("/users/{userId}")
    public ApiResponse<User> updateUserInfo(
            @PathVariable Long userId,
            @RequestBody Map<String, String> updates) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            User user = userService.getById(userId);
            if (user == null) {
                return ApiResponse.fail(404, "用户不存在");
            }

            // 更新真实姓名
            if (updates.containsKey("realName")) {
                user.setRealName(updates.get("realName"));
            }
            // 更新手机号
            if (updates.containsKey("phone")) {
                String phone = updates.get("phone");
                if (phone != null && !phone.isEmpty()) {
                    // 检查手机号是否已被其他用户使用
                    User existingUser = userService.getUserByPhone(phone);
                    if (existingUser != null && !existingUser.getId().equals(userId)) {
                        return ApiResponse.fail(400, "该手机号已被其他用户使用");
                    }
                }
                user.setPhone(phone);
            }
            // 更新邮箱
            if (updates.containsKey("email")) {
                String email = updates.get("email");
                if (email != null && !email.isEmpty()) {
                    // 检查邮箱是否已被其他用户使用
                    User existingUser = userService.getUserByEmail(email);
                    if (existingUser != null && !existingUser.getId().equals(userId)) {
                        return ApiResponse.fail(400, "该邮箱已被其他用户使用");
                    }
                }
                user.setEmail(email);
            }

            userService.updateById(user);
            user.setPassword(null); // 隐藏密码
            log.info("管理员更新用户信息，userId: {}", userId);
            return ApiResponse.success(user);
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 管理员批准申请
     */
    @PostMapping("/applications/{applicationId}/approve")
    public ApiResponse<Void> adminApproveApplication(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String approvalDetail) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Long adminId = getCurrentUserId();
            if (adminId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            Application application = applicationService.getById(applicationId);
            if (application == null) {
                return ApiResponse.fail(404, "申请不存在");
            }

            // 管理员强制批准
            applicationService.approveApplication(applicationId, adminId,
                approvalDetail != null ? approvalDetail : "管理员批准");

            log.info("管理员批准申请，applicationId: {}, adminId: {}", applicationId, adminId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("管理员批准申请失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 管理员驳回申请
     */
    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<Void> adminRejectApplication(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String rejectReason) {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            Long adminId = getCurrentUserId();
            if (adminId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            Application application = applicationService.getById(applicationId);
            if (application == null) {
                return ApiResponse.fail(404, "申请不存在");
            }

            // 管理员强制驳回
            applicationService.rejectApplication(applicationId, adminId,
                rejectReason != null ? rejectReason : "管理员驳回");

            log.info("管理员驳回申请，applicationId: {}, adminId: {}", applicationId, adminId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("管理员驳回申请失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 获取 MiMo 配置（管理员用）
     */
    @GetMapping("/mimo/config")
    public ApiResponse<MimoConfigResponse> getMimoConfig() {
        try {
            if (!isCurrentUserAdmin()) {
                return ApiResponse.fail(403, "没有管理员权限");
            }

            String apiKey = miMoConfig.getApiKey();
            boolean apiKeyConfigured = isConfiguredApiKey(apiKey);
            MimoConfigResponse response = MimoConfigResponse.builder()
                    .enabled(miMoConfig.getEnabled())
                    .baseUrl(miMoConfig.getBaseUrl())
                    .asrModel(miMoConfig.getAsrModel())
                    .chatModel(miMoConfig.getChatModel())
                    .apiKeyConfigured(apiKeyConfigured)
                    .apiKeyMasked(apiKeyConfigured ? maskApiKey(apiKey) : "")
                    .build();

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("获取 MiMo 配置失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    private boolean isCurrentUserAdmin() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }

        User user = userService.getById(userId);
        return user != null && user.isAdmin();
    }

    private boolean isConfiguredApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }

        String normalized = apiKey.trim().toLowerCase();
        return !"your-mimo-api-key".equals(normalized)
                && !"your-api-key".equals(normalized)
                && !"your_api_key".equals(normalized)
                && !"replace-with-your-mimo-api-key".equals(normalized)
                && !normalized.contains("请替换");
    }

    private String maskApiKey(String apiKey) {
        if (!isConfiguredApiKey(apiKey)) {
            return "";
        }

        String trimmed = apiKey.trim();
        int length = trimmed.length();
        if (length <= 3) {
            return "***";
        }
        if (length <= 8) {
            return trimmed.substring(0, 1) + "***" + trimmed.substring(length - 1);
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(length - 3);
    }
}
