package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationCommentDTO;
import com.approval.system.dto.CommentCreateRequest;
import com.approval.system.dto.CommentUpdateRequest;
import com.approval.system.service.IApplicationCommentService;
import com.approval.system.service.IApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/comments")
public class ApplicationCommentController {

    @Autowired
    private IApplicationCommentService commentService;

    @Autowired
    private IApplicationService applicationService;

    /**
     * 获取申请的所有评论（申请人、审批人或管理员）
     */
    @GetMapping("/application/{applicationId}")
    public ApiResponse<List<ApplicationCommentDTO>> getApplicationComments(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId) {
        applicationService.assertApplicationParticipantOrAdmin(applicationId, userId);
        return ApiResponse.success(commentService.getApplicationComments(applicationId));
    }

    /**
     * 创建评论
     */
    @PostMapping
    public ApiResponse<ApplicationCommentDTO> createComment(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CommentCreateRequest request) {
        applicationService.assertApplicationParticipantOrAdmin(request.getApplicationId(), userId);
        ApplicationCommentDTO comment = commentService.createComment(
                request.getApplicationId(), userId, request.getContent(), request.getParentId());
        return ApiResponse.success(comment);
    }

    /**
     * 更新评论（仅作者本人）
     */
    @PutMapping("/{commentId}")
    public ApiResponse<ApplicationCommentDTO> updateComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ApiResponse.success(commentService.updateComment(commentId, userId, request.getContent()));
    }

    /**
     * 删除评论（仅作者本人）
     */
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal Long userId, @PathVariable Long commentId) {
        commentService.deleteComment(commentId, userId);
        return ApiResponse.success(null);
    }
}
