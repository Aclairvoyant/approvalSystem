package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationCommentDTO;
import com.approval.system.service.IApplicationCommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/comments")
public class ApplicationCommentController {

    @Autowired
    private IApplicationCommentService commentService;

    /**
     * 获取申请的所有评论
     */
    @GetMapping("/application/{applicationId}")
    public ApiResponse<List<ApplicationCommentDTO>> getApplicationComments(
            @PathVariable Long applicationId) {
        try {
            List<ApplicationCommentDTO> comments = commentService.getApplicationComments(applicationId);
            return ApiResponse.success(comments);
        } catch (Exception e) {
            log.error("获取评论失败，applicationId: {}", applicationId, e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 创建评论
     */
    @PostMapping
    public ApiResponse<ApplicationCommentDTO> createComment(
            @RequestBody Map<String, Object> request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            Long applicationId = Long.valueOf(request.get("applicationId").toString());
            String content = request.get("content").toString();
            Long parentId = request.get("parentId") != null
                    ? Long.valueOf(request.get("parentId").toString())
                    : null;

            if (content == null || content.trim().isEmpty()) {
                return ApiResponse.fail(400, "评论内容不能为空");
            }

            ApplicationCommentDTO comment = commentService.createComment(applicationId, userId, content, parentId);
            return ApiResponse.success(comment);
        } catch (Exception e) {
            log.error("创建评论失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新评论
     */
    @PutMapping("/{commentId}")
    public ApiResponse<ApplicationCommentDTO> updateComment(
            @PathVariable Long commentId,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            String content = request.get("content");
            if (content == null || content.trim().isEmpty()) {
                return ApiResponse.fail(400, "评论内容不能为空");
            }

            ApplicationCommentDTO comment = commentService.updateComment(commentId, userId, content);
            return ApiResponse.success(comment);
        } catch (Exception e) {
            log.error("更新评论失败，commentId: {}", commentId, e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            commentService.deleteComment(commentId, userId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除评论失败，commentId: {}", commentId, e);
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
