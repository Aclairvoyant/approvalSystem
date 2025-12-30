package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.entity.Application;
import com.approval.system.entity.User;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IUserRelationService;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard/stats")
    public ApiResponse<Map<String, Object>> getDashboardStats() {
        try {
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
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}
