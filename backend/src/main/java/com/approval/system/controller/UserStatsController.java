package com.approval.system.controller;

import com.approval.system.common.enums.ApplicationStatusEnum;
import com.approval.system.common.enums.RelationTypeEnum;
import com.approval.system.common.response.ApiResponse;
import com.approval.system.entity.Application;
import com.approval.system.entity.UserRelation;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 当前登录用户的个人统计（移动端个人页使用）。
 * URL 历史上挂在 /api/admin 下，为了不改前端保留原路径；SecurityConfig 对它单独放行给所有登录用户，
 * 它不在 AdminController 里，所以不受管理员权限限制。
 */
@RestController
public class UserStatsController {

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private IUserRelationService userRelationService;

    @GetMapping("/api/admin/user/stats")
    public ApiResponse<Map<String, Object>> getUserStats(@AuthenticationPrincipal Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 我发起的申请统计
        stats.put("myPending", countApplications(Application::getApplicantId, userId, ApplicationStatusEnum.PENDING));
        stats.put("myApproved", countApplications(Application::getApplicantId, userId, ApplicationStatusEnum.APPROVED));
        stats.put("myRejected", countApplications(Application::getApplicantId, userId, ApplicationStatusEnum.REJECTED));
        // 待我审批的申请
        stats.put("pendingForMe", countApplications(Application::getApproverId, userId, ApplicationStatusEnum.PENDING));
        // 我的对象数量：一段关系只存一行，发起方在 user_id、接受方在 related_user_id，两边都要算
        stats.put("myRelations", userRelationService.count(new LambdaQueryWrapper<UserRelation>()
                .and(w -> w.eq(UserRelation::getUserId, userId).or().eq(UserRelation::getRelatedUserId, userId))
                .eq(UserRelation::getRelationType, RelationTypeEnum.ESTABLISHED.getCode())));

        return ApiResponse.success(stats);
    }

    private long countApplications(SFunction<Application, Long> userColumn, Long userId, ApplicationStatusEnum status) {
        return applicationService.count(new LambdaQueryWrapper<Application>()
                .eq(userColumn, userId)
                .eq(Application::getStatus, status.getCode()));
    }
}
